import { useState, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, FileText, Upload, Link2, Sparkles, Check, X, RefreshCw, Image as ImageIcon } from 'lucide-react';
import PageShell from '../../../components/shared/PageShell';
import Button from '../../../components/shared/Button';
import { useToast } from '../../../context/notifications/ToastContext';
import { PROFESSOR_NAV_ITEMS } from '../../../data/navigationData';
import {
  createLearningModule,
  generateLesson,
  approveLesson,
  declineLesson,
} from '../../../services/professor/professorService';
import styles from '../styles/LessonEditor.module.css';

const MAX_SOURCE_FILES = 10;

// ⚠️ KNOWN BACKEND GAP: there is no GET endpoint for a professor to fetch
// an existing AI Learning Module for a week (the only GET-by-week route,
// on StudentAiLearningController, only returns APPROVED modules and 404s
// otherwise). Until your backend team adds one, this page always starts
// from a blank "create new module" state — it can't resume/edit a
// module that was already created earlier for this week. Ask your
// backend team for something like:
//   GET /api/professor/ai-learning-modules/week/{weekId}
// that returns the module regardless of status (or null/404 if none
// exists yet), so this page can load prior progress instead of always
// starting fresh.

export default function LessonEditor() {
  const { subjectId, weekId } = useParams();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const materialsInputRef = useRef(null);

  // Raw materials the professor provides
  const [lessonText, setLessonText] = useState('');
  const [youtubeLink, setYoutubeLink] = useState('');
  const [aiNotes, setAiNotes] = useState('');
  const [files, setFiles] = useState([]); // File objects, not yet uploaded

  // Module state (populated once createLearningModule() succeeds)
  const [module, setModule] = useState(null); // full AiLearningModuleResponse
  const [isCreating, setIsCreating] = useState(false);
  const [isGenerating, setIsGenerating] = useState(false);
  const [isApproving, setIsApproving] = useState(false);
  const [isDeclining, setIsDeclining] = useState(false);

  function handleFilesPicked(e) {
    const picked = Array.from(e.target.files || []);
    if (picked.length === 0) return;

    const availableSlots = MAX_SOURCE_FILES - files.length;
    if (availableSlots <= 0) {
      showToast(`You can upload up to ${MAX_SOURCE_FILES} files. Remove some before adding more.`, 'error');
      e.target.value = '';
      return;
    }

    const accepted = picked.slice(0, availableSlots);
    setFiles((prev) => [...prev, ...accepted]);

    if (picked.length > accepted.length) {
      showToast(
        `Only added ${accepted.length} of ${picked.length} files — ${MAX_SOURCE_FILES} file limit reached.`,
        'error'
      );
    }
    e.target.value = '';
  }

  function removeFile(index) {
    setFiles((prev) => prev.filter((_, i) => i !== index));
  }

  async function handleCreateModule() {
    if (!lessonText && !youtubeLink && files.length === 0) {
      showToast('Add at least a lesson text, a YouTube link, or a file first.', 'error');
      return;
    }
    if (isCreating) return;
    setIsCreating(true);
    try {
      const created = await createLearningModule(weekId, { lessonText, youtubeLink, aiNotes, files });
      setModule(created);
      showToast('Module created. You can now generate the lesson with AI.', 'success');
    } catch (err) {
      console.error(err);
      showToast("Couldn't create the module. Please try again.", 'error');
    } finally {
      setIsCreating(false);
    }
  }

  async function handleGenerate() {
    if (!module || isGenerating) return;
    setIsGenerating(true);
    try {
      const updated = await generateLesson(module.id);
      setModule(updated);
      showToast('Lesson generated. Please review before approving.', 'success');
    } catch (err) {
      console.error(err);
      showToast("Couldn't generate the lesson. Please try again.", 'error');
    } finally {
      setIsGenerating(false);
    }
  }

  async function handleApprove() {
    if (!module || isApproving) return;
    setIsApproving(true);
    try {
      const updated = await approveLesson(module.id);
      setModule(updated);
      showToast('Lesson approved and published to students.', 'success');
    } catch (err) {
      console.error(err);
      showToast("Couldn't approve the lesson. Please try again.", 'error');
    } finally {
      setIsApproving(false);
    }
  }

  async function handleDecline() {
    if (!module || isDeclining) return;
    setIsDeclining(true);
    try {
      const updated = await declineLesson(module.id);
      setModule(updated);
      showToast('Lesson declined. You can regenerate a new draft.', 'success');
    } catch (err) {
      console.error(err);
      showToast("Couldn't decline the lesson. Please try again.", 'error');
    } finally {
      setIsDeclining(false);
    }
  }

  const aiStatus = module?.aiGenerationStatus; // 'PENDING' | 'COMPLETED' | 'DECLINED' | 'FAILED'
  const lessonStatus = module?.status; // 'PENDING' | 'APPROVED' | 'DECLINED'
  const hasGeneratedContent = aiStatus === 'COMPLETED';

  return (
    <PageShell navItems={PROFESSOR_NAV_ITEMS} sectionLabel="Prof" roleBadge="Professor">
      <header className={styles.topHeader}>
        <div className={styles.topHeaderLeft}>
          <button className={styles.backBtn} onClick={() => navigate('/professor/subjects')}>
            <ArrowLeft size={16} />
            Back
          </button>
          <div>
            <p className={styles.eyebrow}>Subject #{subjectId} · Week #{weekId}</p>
            <h1 className={styles.title}>Lesson Materials</h1>
          </div>
        </div>
      </header>

      <main className={styles.main}>
        {/* --- Step 1: Raw materials --- */}
        <div className={styles.panelCard}>
          <div className={styles.panelHeader}>
            <span className={styles.panelIcon}><FileText size={16} /></span>
            <div>
              <h2 className={styles.panelTitle}>Lesson Materials</h2>
              <p className={styles.panelSubtitle}>
                Add whatever you have — text, a YouTube link, notes, and/or files. The AI mentor will use all of it.
              </p>
            </div>
          </div>

          <div className={styles.field}>
            <label className={styles.label}>Lesson text</label>
            <textarea
              className={styles.textarea}
              rows={6}
              value={lessonText}
              onChange={(e) => setLessonText(e.target.value)}
              placeholder="Paste or write the lesson content here"
              disabled={!!module}
            />
          </div>

          <div className={styles.linkRow}>
            <Link2 size={18} className={styles.linkIcon} />
            <input
              type="text"
              className={styles.input}
              value={youtubeLink}
              onChange={(e) => setYoutubeLink(e.target.value)}
              placeholder="YouTube link (optional)"
              disabled={!!module}
            />
          </div>

          <div className={styles.field}>
            <label className={styles.label}>Notes for the AI mentor (optional)</label>
            <textarea
              className={styles.textarea}
              rows={3}
              value={aiNotes}
              onChange={(e) => setAiNotes(e.target.value)}
              placeholder="Anything the AI should emphasize or avoid"
              disabled={!!module}
            />
          </div>

          <div className={styles.materialsSection}>
            <button
              className={styles.fileUploadBox}
              onClick={() => materialsInputRef.current?.click()}
              disabled={!!module || files.length >= MAX_SOURCE_FILES}
            >
              <Upload size={22} />
              <span className={styles.fileUploadLabel}>Upload Files</span>
              <span className={styles.fileUploadHint}>
                PDFs and images — {files.length}/{MAX_SOURCE_FILES} added
              </span>
            </button>
            <input
              ref={materialsInputRef}
              type="file"
              accept=".pdf,image/*"
              multiple
              hidden
              onChange={handleFilesPicked}
            />

            {files.length > 0 && (
              <ul className={styles.fileList}>
                {files.map((f, i) => (
                  <li key={`${f.name}-${i}`} className={styles.fileListItem}>
                    {f.type.startsWith('image/') ? <ImageIcon size={15} /> : <FileText size={15} />}
                    <span className={styles.fileListName}>{f.name}</span>
                    {!module && (
                      <button className={styles.removeBtn} onClick={() => removeFile(i)} aria-label={`Remove ${f.name}`}>
                        <X size={14} />
                      </button>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </div>

          {!module && (
            <div className={styles.saveRow}>
              <Button onClick={handleCreateModule} isLoading={isCreating}>Save Materials</Button>
            </div>
          )}
        </div>

        {/* --- Step 2: Generate + review --- */}
        {module && (
          <div className={styles.panelCard}>
            <div className={styles.panelHeader}>
              <span className={styles.panelIcon}><Sparkles size={16} /></span>
              <div>
                <h2 className={styles.panelTitle}>AI-Generated Lesson</h2>
                <p className={styles.panelSubtitle}>Review before approving — this is what students will see.</p>
              </div>
            </div>

            {!hasGeneratedContent && (
              <div className={styles.generateRow}>
                <Button onClick={handleGenerate} isLoading={isGenerating}>
                  <Sparkles size={15} style={{ marginRight: 6 }} />
                  {aiStatus === 'FAILED' ? 'Retry Generation' : 'Generate Lesson with AI'}
                </Button>
              </div>
            )}

            {hasGeneratedContent && (
              <div className={styles.aiReviewCard}>
                <div className={styles.aiReviewHeader}>
                  <span className={styles.aiReviewTitle}><Sparkles size={14} /> Generated Content</span>
                  <span
                    className={`${styles.statusBadge} ${
                      lessonStatus === 'APPROVED' ? styles.statusApproved : styles.statusPending
                    }`}
                  >
                    {lessonStatus === 'PENDING' && 'Pending Review'}
                    {lessonStatus === 'APPROVED' && 'Approved'}
                    {lessonStatus === 'DECLINED' && 'Declined'}
                  </span>
                </div>

                <div className={styles.field}>
                  <label className={styles.label}>Objectives</label>
                  <p className={styles.aiGeneratedText}>{module.generatedObjectives}</p>
                </div>
                <div className={styles.field}>
                  <label className={styles.label}>Knowledge</label>
                  <p className={styles.aiGeneratedText}>{module.generatedKnowledge}</p>
                </div>
                <div className={styles.field}>
                  <label className={styles.label}>Examples</label>
                  <p className={styles.aiGeneratedText}>{module.generatedExamples}</p>
                </div>
                <div className={styles.field}>
                  <label className={styles.label}>Summary</label>
                  <p className={styles.aiGeneratedText}>{module.generatedSummary}</p>
                </div>

                {lessonStatus !== 'APPROVED' && (
                  <div className={styles.aiReviewActions}>
                    <Button onClick={handleApprove} isLoading={isApproving}>
                      <Check size={15} style={{ marginRight: 6 }} />
                      Approve
                    </Button>
                    <Button variant="secondary" onClick={handleDecline} isLoading={isDeclining}>
                      <X size={15} style={{ marginRight: 6 }} />
                      Decline
                    </Button>
                    <Button variant="secondary" onClick={handleGenerate} isLoading={isGenerating}>
                      <RefreshCw size={15} style={{ marginRight: 6 }} />
                      Regenerate
                    </Button>
                  </div>
                )}
              </div>
            )}
          </div>
        )}
      </main>
    </PageShell>
  );
}