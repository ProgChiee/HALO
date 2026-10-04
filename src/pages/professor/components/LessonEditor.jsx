import { useState, useRef, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, FileText, Upload, Link2, Sparkles, Check, X, RefreshCw, Image as ImageIcon } from 'lucide-react';
import PageShell from '../../../components/shared/PageShell';
import Button from '../../../components/shared/Button';
import { useToast } from '../../../context/notifications/useToast';
import { PROFESSOR_NAV_ITEMS } from '../../../data/navigationData';
import {
  createLearningModule,
  getLearningModuleByWeek,
  updateLearningModule,
  uploadModuleFile,
  deleteModuleFile,
  generateLesson,
  approveLesson,
  declineLesson,
} from '../../../services/professor/professorService';
import styles from '../styles/LessonEditor.module.css';

const MAX_SOURCE_FILES = 10;
const MAX_FILE_SIZE = 3 * 1024 * 1024;
const ALLOWED_FILE_TYPES = new Set(['application/pdf', 'image/png', 'image/jpeg']);

export default function LessonEditor() {
  const { subjectId, weekId } = useParams();
  return <RouteLessonEditor key={JSON.stringify([subjectId, weekId])} subjectId={subjectId} weekId={weekId} />;
}

function RouteLessonEditor({ subjectId, weekId }) {
  const navigate = useNavigate();
  const { showToast } = useToast();
  const materialsInputRef = useRef(null);

  // Raw materials the professor provides
  const [lessonText, setLessonText] = useState('');
  const [youtubeLink, setYoutubeLink] = useState('');
  const [aiNotes, setAiNotes] = useState('');
  const [files, setFiles] = useState([]); // File objects, not yet uploaded

  // Existing draft or published module, including saved files.
  const [module, setModule] = useState(null); // full AiLearningModuleResponse
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  const [pendingAction, setPendingAction] = useState(null);
  const actionLock = useRef(false);
  const lifecycle = useRef(null);
  useEffect(() => {
    const controller = new AbortController();
    lifecycle.current = controller;
    const isCurrent = () => lifecycle.current === controller && !controller.signal.aborted;
    getLearningModuleByWeek(weekId, { signal: controller.signal })
      .then((existing) => {
        if (!isCurrent()) return;
        setModule(existing);
        setLessonText(existing?.lessonText ?? '');
        setYoutubeLink(existing?.youtubeLink ?? '');
        setAiNotes(existing?.aiNotes ?? '');
        setFiles([]);
        setLoadError('');
      })
      .catch(() => {
        if (isCurrent()) setLoadError('Could not load this week. Retry before saving materials.');
      })
      .finally(() => {
        if (isCurrent()) setLoading(false);
      });
    return () => { controller.abort(); };
  }, [subjectId, weekId, reloadKey]);
  const isSaving = pendingAction === 'save';
  const isGenerating = pendingAction === 'generate';
  const isApproving = pendingAction === 'approve';
  const isDeclining = pendingAction === 'decline';
  const isBusy = pendingAction !== null || loading || !!loadError;
  const isPublished = module?.status === 'APPROVED';
  const savedFiles = module?.files ?? [];
  const textChanged = !!module && (
    lessonText !== (module.lessonText ?? '') ||
    youtubeLink !== (module.youtubeLink ?? '') ||
    aiNotes !== (module.aiNotes ?? '')
  );
  const hasUnsavedChanges = textChanged || files.length > 0;
  const editingDisabled = isBusy || isPublished;

  function handleFilesPicked(e) {
    if (editingDisabled) return;
    const picked = Array.from(e.target.files || []);

    if (picked.length === 0) return;

    // Check allowed file types and empty files
    if (
      picked.some(
        (file) =>
          !ALLOWED_FILE_TYPES.has(file.type) ||
          file.size === 0
      )
    ) {
      showToast(
        'Choose non-empty PDF, PNG, or JPG/JPEG files only.',
        'error'
      );

      e.target.value = '';
      return;
    }

    // Check file size - maximum 3 MB per file
    const oversizedFile = picked.find(
      (file) =>
        file.size > MAX_FILE_SIZE
    );

    if (oversizedFile) {
      showToast(
        `${oversizedFile.name} exceeds the 3 MB per-file limit.`,
        'error'
      );

      e.target.value = '';
      return;
    }

    const availableSlots = MAX_SOURCE_FILES - savedFiles.length - files.length;

    if (availableSlots <= 0) {
      showToast(
        `You can upload up to ${MAX_SOURCE_FILES} files. Remove some before adding more.`,
        'error'
      );

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

  async function runAction(action, request, successMessage) {
    const identity = lifecycle.current;
    const isCurrent = () => identity !== null && lifecycle.current === identity && !identity.signal.aborted;
    if (!isCurrent() || actionLock.current || isBusy || isPublished) return;
    actionLock.current = true;
    setPendingAction(action);
    try {
      const updated = await request(isCurrent);
      if (!isCurrent()) return;
      setModule(updated);
      if (action === 'generate' && updated.aiGenerationStatus !== 'COMPLETED') {
        showToast('The lesson could not be generated from these materials.', 'error');
      } else {
        showToast(successMessage, 'success');
      }
    } catch (error) {
      if (!isCurrent()) return;
      const materialMessages = {
        MODULE_MATERIALS_REQUIRED: 'Upload at least one original lesson file before publishing.',
        MODULE_MATERIAL_UNREADABLE: 'An original lesson file could not be read or indexed. Replace it with a readable file and try again.',
        MODULE_MATERIAL_UNSUPPORTED: 'An original lesson file has an unsupported format. Replace it before publishing.',
        MODULE_MATERIAL_MISMATCH: 'Lesson files do not match this module. Reload and check its attachments before publishing.',
        MODULE_MATERIALS_TOO_LARGE: 'Lesson materials exceed the supported indexing limits. Reduce their size before publishing.',
        MODULE_INDEX_UNAVAILABLE: 'Lesson material preparation is unavailable. Please try publishing again later.',
      };
      if (action === 'approve' && Object.hasOwn(materialMessages, error.response?.data?.code)) {
        showToast(materialMessages[error.response.data.code], 'error', 8000);
        return;
      }
      const approvalStatus = error.response?.status;
      if (action === 'approve' && (!error.response || approvalStatus >= 500 || approvalStatus === 408 || approvalStatus === 409)) {
        try {
          const latest = await getLearningModuleByWeek(weekId, { signal: identity.signal });
          if (!isCurrent()) return;
          if (!latest || latest.id !== module.id) {
            setLoadError('The module changed or is unavailable. Reload before trying to publish again.');
            return;
          }
          setModule(latest);
          setLessonText(latest.lessonText ?? '');
          setYoutubeLink(latest.youtubeLink ?? '');
          setAiNotes(latest.aiNotes ?? '');
          if (latest.status === 'APPROVED') {
            showToast(successMessage, 'success');
          } else {
            showToast('Approval was not confirmed. The lesson is not currently published. The server may still be processing it; reload its status before trying again.', 'error', 8000);
          }
        } catch {
          if (!isCurrent()) return;
          setLoadError('Could not verify whether publishing completed. Retry loading to check its status before approving again.');
        }
        return;
      }
      // Generation clears saved content before calling Gemini. Refresh even on
      // failure so an old preview cannot still be approved in the editor.
      if (action === 'generate') {
        try {
          const latest = await getLearningModuleByWeek(weekId);
          if (!isCurrent()) return;
          setModule(latest);
        } catch {
          if (!isCurrent()) return;
          setLoadError('Could not refresh the generation result. Retry loading before editing or publishing.');
        }
      }
      const status = error.response?.status;
      if (status === 409 && action === 'save' && !module) {
        setLoadError('This week already has a module. Retry loading to open the existing lesson.');
      }
      const serverMessage = error.response?.data?.message;
      const message = typeof serverMessage === 'string' ? serverMessage : status === 403
        ? 'The server refused this request (403). Check your professor session. If other professor pages work, ask the administrator to check the upload error in the server log.'
        : status === 413
          ? 'The upload exceeds the server size limit. Choose smaller files.'
          : "Couldn't update the module. Please try again.";
      if (isCurrent()) showToast(message, 'error', 8000);
    } finally {
      if (isCurrent()) {
        actionLock.current = false;
        setPendingAction(null);
      }
    }
  }

  function handleSaveMaterials() {
    if (!lessonText.trim() && !youtubeLink.trim() && files.length + savedFiles.length === 0) {
      showToast('Add lesson text, a YouTube link, or a file first.', 'error');
      return;
    }
    return runAction('save', async (isCurrent) => {
      const materials = { lessonText, youtubeLink, aiNotes };
      if (!module) {
        const created = await createLearningModule(weekId, { ...materials, files });
        if (isCurrent()) setFiles([]);
        return created;
      }
      let updated = module;
      if (textChanged) {
        updated = await updateLearningModule(module.id, materials);
        if (isCurrent()) setModule(updated);
      }
      // Retain only failed/pending uploads if a later request fails.
      for (const file of files) {
        if (!isCurrent()) return;
        updated = await uploadModuleFile(module.id, file);
        if (isCurrent()) {
          setModule(updated);
          setFiles((remaining) => remaining.filter((item) => item !== file));
        }
      }
      return updated;
    }, 'Materials saved. Generate and review the lesson, then Approve & Publish.');
  }

  function handleDeleteSavedFile(fileId) {
    return runAction('delete', async () => {
      await deleteModuleFile(fileId);
      return {
        ...module, files: savedFiles.filter((file) => file.id !== fileId),
        status: 'PENDING', aiGenerationStatus: 'PENDING',
        generatedObjectives: null, generatedKnowledge: null,
        generatedExamples: null, generatedSummary: null,
      };
    }, 'File removed. Regenerate the lesson before publishing.');
  }

  function handleGenerate() {
    if (!module || hasUnsavedChanges) return;
    return runAction('generate', () => generateLesson(module.id), 'Lesson generated. Please review before approving.');
  }

  function handleApprove() {
    if (!module || hasUnsavedChanges) return;
    return runAction('approve', () => approveLesson(module.id), 'Lesson approved and published to students.');
  }

  function handleDecline() {
    if (!module || hasUnsavedChanges) return;
    return runAction('decline', () => declineLesson(module.id), 'Lesson declined. You can regenerate a new draft.');
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
        {loading && <p role="status">Loading saved lesson...</p>}
        {loadError && (
          <div role="alert">
            <p>{loadError}</p>
            <Button onClick={() => { setLoading(true); setLoadError(''); setReloadKey((key) => key + 1); }}>Retry loading</Button>
          </div>
        )}
        {!loading && !loadError && <>
        <p role="status">
          {isPublished
            ? 'Published: this lesson is available to eligible students. Published materials are read-only.'
            : 'Draft: Save Materials, Generate Lesson with AI, Review, then Approve & Publish.'}
          {hasUnsavedChanges && module ? ' Save your changes before generating or publishing.' : ''}
        </p>
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
              disabled={editingDisabled}
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
              disabled={editingDisabled}
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
              disabled={editingDisabled}
            />
          </div>

          <div className={styles.materialsSection}>
            <button
              className={styles.fileUploadBox}
              onClick={() => materialsInputRef.current?.click()}
              disabled={editingDisabled || savedFiles.length + files.length >= MAX_SOURCE_FILES}
            >
              <Upload size={22} />
              <span className={styles.fileUploadLabel}>Upload Files</span>
              <span className={styles.fileUploadHint}>
              PDF, PNG, JPG/JPEG (max 3 MB each) — {savedFiles.length + files.length}/{MAX_SOURCE_FILES} added
              </span>
            </button>
            <input
              ref={materialsInputRef}
              type="file"
              accept="application/pdf,image/png,image/jpeg,.pdf,.png,.jpg,.jpeg"
              multiple
              hidden
              onChange={handleFilesPicked}
            />

            {savedFiles.length > 0 && (
              <ul className={styles.fileList}>
                {savedFiles.map((file) => (
                  <li key={file.id} className={styles.fileListItem}>
                    <FileText size={15} />
                    <span className={styles.fileListName}>{file.originalFileName} (saved)</span>
                    {!isPublished && (
                      <button className={styles.removeBtn} disabled={isBusy} onClick={() => handleDeleteSavedFile(file.id)} aria-label={`Delete ${file.originalFileName}`}>
                        <X size={14} />
                      </button>
                    )}
                  </li>
                ))}
              </ul>
            )}
            {files.length > 0 && (
              <ul className={styles.fileList}>
                {files.map((f, i) => (
                  <li key={`${f.name}-${i}`} className={styles.fileListItem}>
                    {f.type.startsWith('image/') ? <ImageIcon size={15} /> : <FileText size={15} />}
                    <span className={styles.fileListName}>{f.name}</span>
                    {!isPublished && (
                      <button className={styles.removeBtn} disabled={isBusy} onClick={() => removeFile(i)} aria-label={`Remove ${f.name}`}>
                        <X size={14} />
                      </button>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </div>

          {!isPublished && (
            <div className={styles.saveRow}>
              <Button onClick={handleSaveMaterials} isLoading={isSaving} disabled={isBusy || (!!module && !hasUnsavedChanges)}>Save Materials</Button>
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

            {!hasGeneratedContent && !isPublished && (
              <div className={styles.generateRow}>
                <Button onClick={handleGenerate} isLoading={isGenerating} disabled={isBusy || hasUnsavedChanges}>
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
                    {lessonStatus === 'APPROVED' && 'Published'}
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
                    <Button onClick={handleApprove} isLoading={isApproving} disabled={isBusy || hasUnsavedChanges}>
                      <Check size={15} style={{ marginRight: 6 }} />
                      Approve & Publish
                    </Button>
                    <Button variant="secondary" onClick={handleDecline} isLoading={isDeclining} disabled={isBusy || hasUnsavedChanges}>
                      <X size={15} style={{ marginRight: 6 }} />
                      Decline
                    </Button>
                    <Button variant="secondary" onClick={handleGenerate} isLoading={isGenerating} disabled={isBusy || hasUnsavedChanges}>
                      <RefreshCw size={15} style={{ marginRight: 6 }} />
                      Regenerate
                    </Button>
                  </div>
                )}
              </div>
            )}
          </div>
        )}
        </>}
      </main>
    </PageShell>
  );
}
