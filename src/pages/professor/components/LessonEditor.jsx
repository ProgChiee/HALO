import { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, Plus, X, FileText, Upload, Link2, Type, Sparkles, Check, Pencil, RefreshCw, Image as ImageIcon } from 'lucide-react';
import PageShell from '../../../components/shared/PageShell';
import Button from '../../../components/shared/Button';
import { useToast } from '../../../context/notifications/ToastContext';
import { PROFESSOR_NAV_ITEMS } from '../../../data/navigationData';
import { getSubjectAndWeek, updateWeekDetails, generateLessonFromMaterials } from '../../../services/professor/professorService';
import styles from '../styles/LessonEditor.module.css';

// Keeps the source-materials list from growing unbounded — also keeps
// the mock generation prompt (file names joined together) sane, and
// stands in for the kind of limit a real AI backend would enforce
// (context window / processing time constraints).
const MAX_SOURCE_FILES = 10;

const CONTENT_METHODS = [
  { id: 'file', label: 'Upload File', icon: Upload },
  { id: 'link', label: 'Paste Link', icon: Link2 },
  { id: 'text', label: 'Write Text', icon: Type },
];

export default function LessonEditor() {
  const { subjectId, weekId } = useParams();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const materialsInputRef = useRef(null);

  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [subject, setSubject] = useState(null);
  const [week, setWeek] = useState(null);

  // Kept separate (instead of one shared "isSaving") so clicking Save on
  // one panel (e.g. Objectives) doesn't show a loading/disabled state on
  // unrelated buttons in a different panel (e.g. Save Content).
  const [isSavingObjectives, setIsSavingObjectives] = useState(false);
  const [isSavingContent, setIsSavingContent] = useState(false);
  const [isSaving, setIsSaving] = useState(false); // AI review card (Approve / Save Edited Version)

  // Local editable state
  const [objectives, setObjectives] = useState([]);
  const [contentMethod, setContentMethod] = useState('text');
  const [fileName, setFileName] = useState('');
  const [fileType, setFileType] = useState('');
  const [linkUrl, setLinkUrl] = useState('');
  const [lessonText, setLessonText] = useState('');

  // AI generation (from uploaded source materials) state
  const [sourceFiles, setSourceFiles] = useState([]);
  const [aiGeneratedText, setAiGeneratedText] = useState('');
  const [aiStatus, setAiStatus] = useState('idle'); // 'idle' | 'generating' | 'pending_review' | 'approved'
  const [isGenerating, setIsGenerating] = useState(false);
  const [isEditingGenerated, setIsEditingGenerated] = useState(false);
  const [editedGeneratedText, setEditedGeneratedText] = useState('');

  useEffect(() => {
    let isMounted = true;
    async function loadData() {
      try {
        const result = await getSubjectAndWeek(subjectId, weekId);
        if (!isMounted) return;

        setSubject(result.subject);
        setWeek(result.week);

        if (result.week) {
          setObjectives(result.week.objectives?.length ? result.week.objectives : ['']);
          setContentMethod(result.week.content?.method ?? 'text');
          setFileName(result.week.content?.fileName ?? '');
          setFileType(result.week.content?.fileType ?? '');
          setLinkUrl(result.week.content?.linkUrl ?? '');
          setLessonText(result.week.content?.text ?? '');
          setSourceFiles(result.week.content?.sourceFiles ?? []);
          setAiGeneratedText(result.week.content?.aiGeneratedText ?? '');
          setAiStatus(result.week.content?.aiStatus ?? 'idle');
        }
      } catch (err) {
        console.error(err);
        if (isMounted) setLoadError(true);
      } finally {
        if (isMounted) setIsLoading(false);
      }
    }
    loadData();
    return () => { isMounted = false; };
  }, [subjectId, weekId]);

  // --- Objectives handlers ---
  function updateObjective(index, value) {
    setObjectives((prev) => prev.map((o, i) => (i === index ? value : o)));
  }

  function addObjective() {
    setObjectives((prev) => [...prev, '']);
  }

  function removeObjective(index) {
    setObjectives((prev) => prev.filter((_, i) => i !== index));
  }

  async function saveObjectives() {
    if (isSavingObjectives) return;
    setIsSavingObjectives(true);
    try {
      const cleaned = objectives.map((o) => o.trim()).filter(Boolean);
      await updateWeekDetails(subjectId, weekId, { objectives: cleaned });
      setObjectives(cleaned.length ? cleaned : ['']);
      showToast('Objectives saved.', 'success');
    } catch (err) {
      console.error(err);
      showToast("Couldn't save objectives. Please try again.", 'error');
    } finally {
      setIsSavingObjectives(false);
    }
  }

  // --- Content handlers ---

  // --- Source materials (multiple PDFs/images, used for AI generation) ---
  function handleMaterialsPicked(e) {
    const picked = Array.from(e.target.files || []);
    if (picked.length === 0) return;

    const availableSlots = MAX_SOURCE_FILES - sourceFiles.length;
    if (availableSlots <= 0) {
      showToast(`You can upload up to ${MAX_SOURCE_FILES} files. Remove some before adding more.`, 'error');
      e.target.value = '';
      return;
    }

    const accepted = picked.slice(0, availableSlots);
    const newFiles = accepted.map((file) => ({
      id: `${Date.now()}-${file.name}`,
      name: file.name,
      type: file.type.startsWith('image/') ? 'image' : 'pdf',
    }));
    setSourceFiles((prev) => [...prev, ...newFiles]);

    if (picked.length > accepted.length) {
      showToast(
        `Only added ${accepted.length} of ${picked.length} files — ${MAX_SOURCE_FILES} file limit reached.`,
        'error'
      );
    }

    // Allow re-picking the same file again later (e.g. after removing it)
    e.target.value = '';
  }

  function removeSourceFile(id) {
    setSourceFiles((prev) => prev.filter((f) => f.id !== id));
  }

  async function persistContent(updates) {
    await updateWeekDetails(subjectId, weekId, {
      content: {
        method: contentMethod,
        fileName,
        fileType,
        linkUrl,
        text: lessonText,
        sourceFiles,
        aiGeneratedText,
        aiStatus,
        ...updates,
      },
    });
  }

  async function handleGenerateLesson() {
    if (sourceFiles.length === 0) {
      showToast('Upload at least one file (PDF or image) first.', 'error');
      return;
    }
    if (isGenerating) return;

    setIsGenerating(true);
    setAiStatus('generating');
    try {
      const generated = await generateLessonFromMaterials(sourceFiles);
      setAiGeneratedText(generated);
      setAiStatus('pending_review');
      await persistContent({ aiGeneratedText: generated, aiStatus: 'pending_review', sourceFiles });
      showToast('Lesson generated. Please review before approving.', 'success');
    } catch (err) {
      console.error(err);
      setAiStatus(sourceFiles.length ? 'idle' : 'idle');
      showToast("Couldn't generate the lesson. Please try again.", 'error');
    } finally {
      setIsGenerating(false);
    }
  }

  async function handleRegenerateLesson() {
    if (isGenerating) return;

    setIsGenerating(true);
    try {
      const generated = await generateLessonFromMaterials(sourceFiles);
      setAiGeneratedText(generated);
      setAiStatus('pending_review');
      setIsEditingGenerated(false);
      await persistContent({ aiGeneratedText: generated, aiStatus: 'pending_review' });
      showToast('New draft generated. Please review before approving.', 'success');
    } catch (err) {
      console.error(err);
      showToast("Couldn't regenerate the lesson. Please try again.", 'error');
    } finally {
      setIsGenerating(false);
    }
  }

  async function handleApproveLesson() {
    if (isSaving) return;
    setIsSaving(true);
    try {
      setAiStatus('approved');
      await persistContent({ aiStatus: 'approved' });
      showToast('Lesson approved and published to students.', 'success');
    } catch (err) {
      console.error(err);
      setAiStatus('pending_review');
      showToast("Couldn't approve the lesson. Please try again.", 'error');
    } finally {
      setIsSaving(false);
    }
  }

  function startEditingGenerated() {
    setEditedGeneratedText(aiGeneratedText);
    setIsEditingGenerated(true);
  }

  async function handleSaveEditedLesson() {
    if (isSaving) return;
    setIsSaving(true);
    try {
      const finalText = editedGeneratedText.trim();
      setAiGeneratedText(finalText);
      setAiStatus('approved');
      await persistContent({ aiGeneratedText: finalText, aiStatus: 'approved' });
      setIsEditingGenerated(false);
      showToast('Edited lesson approved and published to students.', 'success');
    } catch (err) {
      console.error(err);
      showToast("Couldn't save your edits. Please try again.", 'error');
    } finally {
      setIsSaving(false);
    }
  }

  async function saveContent() {
    if (isSavingContent) return;
    setIsSavingContent(true);
    try {
      await persistContent({});
      showToast('Content saved.', 'success');
    } catch (err) {
      console.error(err);
      showToast("Couldn't save content. Please try again.", 'error');
    } finally {
      setIsSavingContent(false);
    }
  }

  if (isLoading) {
    return (
      <PageShell navItems={PROFESSOR_NAV_ITEMS} sectionLabel="Prof" roleBadge="Professor">
        <p className={styles.loadingText}>Loading lesson...</p>
      </PageShell>
    );
  }

  if (loadError) {
    return (
      <PageShell navItems={PROFESSOR_NAV_ITEMS} sectionLabel="Prof" roleBadge="Professor">
        <main className={styles.main}>
          <p className={styles.loadingText}>Couldn't load this lesson. Please refresh and try again.</p>
          <Button onClick={() => navigate('/professor/subjects')}>Back to Subjects</Button>
        </main>
      </PageShell>
    );
  }

  if (!subject || !week) {
    return (
      <PageShell navItems={PROFESSOR_NAV_ITEMS} sectionLabel="Prof" roleBadge="Professor">
        <main className={styles.main}>
          <p className={styles.loadingText}>Lesson not found.</p>
          <Button onClick={() => navigate('/professor/subjects')}>Back to Subjects</Button>
        </main>
      </PageShell>
    );
  }

  return (
    <PageShell navItems={PROFESSOR_NAV_ITEMS} sectionLabel="Prof" roleBadge="Professor">
      <header className={styles.topHeader}>
        <div className={styles.topHeaderLeft}>
          <button className={styles.backBtn} onClick={() => navigate('/professor/subjects')}>
            <ArrowLeft size={16} />
            Back
          </button>
          <div>
            <p className={styles.eyebrow}>{subject.title} · {week.title.split(':')[0]}</p>
            <h1 className={styles.title}>{week.title.includes(':') ? week.title.split(':')[1].trim() : week.title}</h1>
          </div>
        </div>
      </header>

      <main className={styles.main}>
        {/* --- Objectives --- */}
        <div className={styles.panelCard}>
          <div className={styles.panelHeader}>
            <span className={styles.panelIcon}><FileText size={16} /></span>
            <div>
              <h2 className={styles.panelTitle}>Learning Objectives</h2>
              <p className={styles.panelSubtitle}>By the end of this lesson, students will be able to:</p>
            </div>
          </div>

          <div className={styles.objectivesList}>
            {objectives.map((obj, i) => (
              <div key={i} className={styles.objectiveRow}>
                <span className={styles.objectiveNumber}>{i + 1}</span>
                <input
                  type="text"
                  className={styles.objectiveInput}
                  value={obj}
                  onChange={(e) => updateObjective(i, e.target.value)}
                  placeholder="for demonstration, examples, or additional explaination"
                />
                <button
                  className={styles.removeBtn}
                  onClick={() => removeObjective(i)}
                  aria-label="Remove objective"
                >
                  <X size={14} />
                </button>
              </div>
            ))}
          </div>

          <button className={styles.addRowBtn} onClick={addObjective}>
            <Plus size={14} />
            Add Objective
          </button>

          <div className={styles.saveRow}>
            <Button onClick={saveObjectives} isLoading={isSavingObjectives}>Save Objectives</Button>
          </div>
        </div>

        {/* --- Content --- */}
        <div className={styles.panelCard}>
          <div className={styles.panelHeader}>
            <span className={styles.panelIcon}><FileText size={16} /></span>
            <div>
              <h2 className={styles.panelTitle}>Lesson Content</h2>
              <p className={styles.panelSubtitle}>Choose how you'd like to provide this lesson's material</p>
            </div>
          </div>

          <div className={styles.methodTabs}>
            {CONTENT_METHODS.map((m) => (
              <button
                key={m.id}
                className={`${styles.methodTab} ${contentMethod === m.id ? styles.methodTabActive : ''}`}
                onClick={() => setContentMethod(m.id)}
              >
                <m.icon size={14} />
                {m.label}
              </button>
            ))}
          </div>

          {contentMethod === 'file' && (
            <div className={styles.materialsSection}>
              <button
                className={styles.fileUploadBox}
                onClick={() => materialsInputRef.current?.click()}
                disabled={sourceFiles.length >= MAX_SOURCE_FILES}
              >
                <Upload size={22} />
                <span className={styles.fileUploadLabel}>Upload Source Materials</span>
                <span className={styles.fileUploadHint}>
                  {sourceFiles.length >= MAX_SOURCE_FILES
                    ? `Limit reached (${MAX_SOURCE_FILES} files max)`
                    : `PDFs and images — ${sourceFiles.length}/${MAX_SOURCE_FILES} uploaded`}
                </span>
              </button>
              <input
                ref={materialsInputRef}
                type="file"
                accept=".pdf,image/*"
                multiple
                hidden
                onChange={handleMaterialsPicked}
              />

              {sourceFiles.length > 0 && (
                <ul className={styles.fileList}>
                  {sourceFiles.map((f) => (
                    <li key={f.id} className={styles.fileListItem}>
                      {f.type === 'image' ? <ImageIcon size={15} /> : <FileText size={15} />}
                      <span className={styles.fileListName}>{f.name}</span>
                      <button
                        className={styles.removeBtn}
                        onClick={() => removeSourceFile(f.id)}
                        aria-label={`Remove ${f.name}`}
                      >
                        <X size={14} />
                      </button>
                    </li>
                  ))}
                </ul>
              )}

              <div className={styles.generateRow}>
                <Button
                  variant="secondary"
                  onClick={handleGenerateLesson}
                  isLoading={isGenerating && aiStatus === 'generating'}
                  disabled={sourceFiles.length === 0}
                >
                  <Sparkles size={15} style={{ marginRight: 6 }} />
                  Generate Lesson with AI
                </Button>
              </div>

              {aiStatus !== 'idle' && (isGenerating || aiGeneratedText) && (
                <div className={styles.aiReviewCard}>
                  <div className={styles.aiReviewHeader}>
                    <span className={styles.aiReviewTitle}>
                      <Sparkles size={14} /> AI-Generated Lesson
                    </span>
                    <span
                      className={`${styles.statusBadge} ${
                        aiStatus === 'approved' ? styles.statusApproved : styles.statusPending
                      }`}
                    >
                      {aiStatus === 'generating' && 'Generating...'}
                      {aiStatus === 'pending_review' && 'Pending Review'}
                      {aiStatus === 'approved' && 'Approved'}
                    </span>
                  </div>

                  {isGenerating ? (
                    <p className={styles.aiGeneratingText}>AI Mentor is drafting the lesson from your materials...</p>
                  ) : isEditingGenerated ? (
                    <>
                      <textarea
                        className={styles.textarea}
                        rows={8}
                        value={editedGeneratedText}
                        onChange={(e) => setEditedGeneratedText(e.target.value)}
                      />
                      <div className={styles.aiReviewActions}>
                        <Button onClick={handleSaveEditedLesson} isLoading={isSaving}>
                          <Check size={15} style={{ marginRight: 6 }} />
                          Save Edited Version
                        </Button>
                        <Button variant="secondary" onClick={() => setIsEditingGenerated(false)} disabled={isSaving}>
                          Cancel
                        </Button>
                      </div>
                    </>
                  ) : (
                    <>
                      <p className={styles.aiGeneratedText}>{aiGeneratedText}</p>
                      {aiStatus !== 'approved' && (
                        <div className={styles.aiReviewActions}>
                          <Button onClick={handleApproveLesson} isLoading={isSaving}>
                            <Check size={15} style={{ marginRight: 6 }} />
                            Approve
                          </Button>
                          <Button variant="secondary" onClick={startEditingGenerated} disabled={isSaving || isGenerating}>
                            <Pencil size={15} style={{ marginRight: 6 }} />
                            Edit
                          </Button>
                          <Button variant="secondary" onClick={handleRegenerateLesson} isLoading={isGenerating}>
                            <RefreshCw size={15} style={{ marginRight: 6 }} />
                            Regenerate
                          </Button>
                        </div>
                      )}
                      {aiStatus === 'approved' && (
                        <div className={styles.aiReviewActions}>
                          <Button variant="secondary" onClick={startEditingGenerated}>
                            <Pencil size={15} style={{ marginRight: 6 }} />
                            Edit Approved Lesson
                          </Button>
                          <Button variant="secondary" onClick={handleRegenerateLesson} isLoading={isGenerating}>
                            <RefreshCw size={15} style={{ marginRight: 6 }} />
                            Regenerate
                          </Button>
                        </div>
                      )}
                    </>
                  )}
                </div>
              )}
            </div>
          )}

          {contentMethod === 'link' && (
            <div className={styles.linkRow}>
              <Link2 size={18} className={styles.linkIcon} />
              <input
                type="text"
                className={styles.input}
                value={linkUrl}
                onChange={(e) => setLinkUrl(e.target.value)}
                placeholder="https://drive.google.com/... or any hosted material link"
              />
            </div>
          )}

          {contentMethod === 'text' && (
            <div className={styles.field}>
              <label className={styles.label}>Full lesson text</label>
              <textarea
                className={styles.textarea}
                rows={10}
                value={lessonText}
                onChange={(e) => setLessonText(e.target.value)}
                placeholder="Additional lesson content and notes to ai mentor"
              />
            </div>
          )}

          <div className={styles.saveRow}>
            <Button onClick={saveContent} isLoading={isSavingContent}>Save Content</Button>
          </div>
        </div>
      </main>
    </PageShell>
  );
}