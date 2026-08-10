import { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, Plus, X, FileText, Upload, Link2, Type } from 'lucide-react';
import PageShell from '../../../components/shared/PageShell';
import Button from '../../../components/shared/Button';
import { useToast } from '../../../context/notifications/ToastContext';
import { PROFESSOR_NAV_ITEMS } from '../../../data/navigationData';
import { getSubjectAndWeek, updateWeekDetails } from '../../../services/professor/professorService';
import styles from '../styles/LessonEditor.module.css';

const CONTENT_METHODS = [
  { id: 'file', label: 'Upload File', icon: Upload },
  { id: 'link', label: 'Paste Link', icon: Link2 },
  { id: 'text', label: 'Write Text', icon: Type },
];

export default function LessonEditor() {
  const { subjectId, weekId } = useParams();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const pdfInputRef = useRef(null);
  const docxInputRef = useRef(null);

  const [isLoading, setIsLoading] = useState(true);
  const [subject, setSubject] = useState(null);
  const [week, setWeek] = useState(null);
  const [isSaving, setIsSaving] = useState(false);

  // Local editable state
  const [objectives, setObjectives] = useState([]);
  const [contentMethod, setContentMethod] = useState('text');
  const [fileName, setFileName] = useState('');
  const [fileType, setFileType] = useState('');
  const [linkUrl, setLinkUrl] = useState('');
  const [lessonText, setLessonText] = useState('');

  useEffect(() => {
    let isMounted = true;
    async function loadData() {
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
      }

      setIsLoading(false);
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
    setIsSaving(true);
    const cleaned = objectives.map((o) => o.trim()).filter(Boolean);
    await updateWeekDetails(subjectId, weekId, { objectives: cleaned });
    setObjectives(cleaned.length ? cleaned : ['']);
    showToast('Objectives saved.', 'success');
    setIsSaving(false);
  }

  // --- Content handlers ---
  function handleFilePicked(e, type) {
    const file = e.target.files?.[0];
    if (file) {
      setFileName(file.name);
      setFileType(type);
    }
  }

  async function saveContent() {
    setIsSaving(true);
    await updateWeekDetails(subjectId, weekId, {
      content: { method: contentMethod, fileName, fileType, linkUrl, text: lessonText },
    });
    showToast('Content saved.', 'success');
    setIsSaving(false);
  }

  if (isLoading) {
    return (
      <PageShell navItems={PROFESSOR_NAV_ITEMS} sectionLabel="Prof" roleBadge="Professor">
        <p className={styles.loadingText}>Loading lesson...</p>
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
            <Button onClick={saveObjectives} isLoading={isSaving}>Save Objectives</Button>
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
            <div className={styles.fileUploadRow}>
              <button className={styles.fileUploadBox} onClick={() => pdfInputRef.current?.click()}>
                <FileText size={22} />
                <span className={styles.fileUploadLabel}>Upload PDF</span>
                <span className={styles.fileUploadHint}>Click to browse</span>
              </button>
              <input
                ref={pdfInputRef}
                type="file"
                accept=".pdf"
                hidden
                onChange={(e) => handleFilePicked(e, 'pdf')}
              />

              <button className={styles.fileUploadBox} onClick={() => docxInputRef.current?.click()}>
                <FileText size={22} />
                <span className={styles.fileUploadLabel}>Upload DOCX</span>
                <span className={styles.fileUploadHint}>Click to browse</span>
              </button>
              <input
                ref={docxInputRef}
                type="file"
                accept=".docx"
                hidden
                onChange={(e) => handleFilePicked(e, 'docx')}
              />
            </div>
          )}

          {contentMethod === 'file' && fileName && (
            <p className={styles.fileChosenNote}>
              <FileText size={13} /> {fileName}
            </p>
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
            <Button onClick={saveContent} isLoading={isSaving}>Save Content</Button>
          </div>
        </div>
      </main>
    </PageShell>
  );
}