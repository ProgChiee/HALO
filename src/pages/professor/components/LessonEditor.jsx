import Topbar from '../../../components/shared/Topbar';
import LessonRevision from './LessonRevision';
import QuizRevision from './QuizRevision';
import RevisionHistory from './RevisionHistory';
import LessonWorkspaceTabs from './LessonWorkspaceTabs';
import { GeneratedLessonSections, PublishedMaterials } from './LessonContent';
import { youtubeVideoId } from '../../../utils/youtubeVideo';
import { VideoPreview } from '../../../components/shared/VideoLearning';
import { professorErrorMessage } from '../../../utils/professorErrors';
import { useState, useRef, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, FileText, Upload, Link2, Sparkles, Check, X, RefreshCw, Image as ImageIcon } from 'lucide-react';
import PageShell from './ProfessorPageShell';
import Button from '../../../components/shared/Button';
import { useToast } from '../../../context/notifications/useToast';
import { PROFESSOR_NAV_ITEMS } from '../../../data/navigationData';
import { useProfessorWorkflow } from '../../../context/professor/useProfessorWorkflow';
import styles from '../styles/LessonEditor.module.css';

const MAX_SOURCE_FILES = 10;
const MAX_FILE_SIZE = 3 * 1024 * 1024;
const ALLOWED_FILE_TYPES = new Set(['application/pdf', 'image/png', 'image/jpeg']);

export default function LessonEditor() {
  const { subjectId, weekId } = useParams();
  return <RouteLessonEditor key={JSON.stringify([subjectId, weekId])} subjectId={subjectId} weekId={weekId} />;
}

function RouteLessonEditor({ subjectId, weekId }) {
  const workflow = useProfessorWorkflow();
  const { createLearningModule,
  getLearningModuleByWeek,
  updateLearningModule,
  uploadModuleFile,
  deleteModuleFile,
  generateLesson,
  approveLesson,
  declineLesson, } = workflow.api;
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
      .catch((error) => {
        if (isCurrent()) setLoadError(professorErrorMessage(error, 'Could not load this week. Retry before saving materials.'));
      })
      .finally(() => {
        if (isCurrent()) setLoading(false);
      });
    return () => { controller.abort(); };
  }, [subjectId, weekId, reloadKey, getLearningModuleByWeek]);
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
  const editingDisabled = isBusy || isPublished || workflow.preview;

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
      const message = professorErrorMessage(error, "Couldn't update the module. Please try again.");
      if (isCurrent()) showToast(message, 'error', 8000);
    } finally {
      if (isCurrent()) {
        actionLock.current = false;
        setPendingAction(null);
      }
    }
  }

  function handleSaveMaterials() {
    if (youtubeLink.trim() && !youtubeVideoId(youtubeLink)) { showToast('Please provide a valid YouTube video URL.', 'error'); return; }
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
    <PageShell responsive navItems={PROFESSOR_NAV_ITEMS} sectionLabel="Prof" roleBadge="Professor">
      <Topbar>
        <div className={styles.topHeaderLeft}>
          <button className={styles.backBtn} onClick={() => navigate(workflow.basePath + '/subjects')}>
            <ArrowLeft size={16} />
            Back
          </button>
          <div>
            <p className={styles.eyebrow}>Subject #{module?.subjectId ?? subjectId} · {module?.weekNumber != null ? `Week ${module.weekNumber}` : `Week record #${weekId}`}</p>
            <h1 className={styles.title}>Lesson Management</h1>
          </div>
        </div>
        {!loading && !loadError && <span className={`${styles.statusBadge} ${isPublished ? styles.statusApproved : styles.statusPending}`}>{isPublished ? 'Published' : lessonStatus === 'DECLINED' ? 'Declined' : 'Draft'}</span>}
      </Topbar>

      <main className={styles.main}>
        {loading && <p role="status">Loading saved lesson...</p>}
        {loadError && (
          <div role="alert">
            <p>{loadError}</p>
            <Button onClick={() => { setLoading(true); setLoadError(''); setReloadKey((key) => key + 1); }}>Retry loading</Button>
          </div>
        )}
        {!loading && !loadError && <>
        <p role="status" className={styles.workspaceNotice}>
          {workflow.preview ? "Read-only Professor Preview. Lesson materials and generated content cannot be changed." : isPublished
            ? 'Published: this lesson is available to eligible students. Published materials are read-only.'
            : 'Draft: Save Materials, Generate Lesson with AI, Review, then Approve & Publish.'}
          {hasUnsavedChanges && module ? ' Save your changes before generating or publishing.' : ''}
        </p>
        <LessonWorkspaceTabs
          assessment={isPublished && !workflow.preview ? <QuizRevision key={'quiz-' + module.id + '-' + reloadKey} moduleId={module.id} /> : <section className={styles.panelCard}><h2 className={styles.panelTitle}>Assessment</h2><p className={styles.panelSubtitle}>{workflow.preview ? 'Assessment management is not available in read-only Professor Preview.' : 'Publish the lesson before managing its assessment.'}</p></section>}
          history={module && !workflow.preview ? <section className={styles.panelCard}><RevisionHistory key={module.id + '-' + reloadKey} moduleId={module.id} embedded /></section> : <section className={styles.panelCard}><h2 className={styles.panelTitle}>Revision History</h2><p className={styles.panelSubtitle}>{workflow.preview ? 'Revision history is not available in read-only Professor Preview.' : 'Published revision snapshots will appear here when available.'}</p></section>}>
        {isPublished && !workflow.preview && <LessonRevision key={module.id} moduleId={module.id} onPublished={() => { setLoading(true); setReloadKey(key => key + 1); }} />}
        {/* --- Step 1: Raw materials --- */}
        {isPublished ? <PublishedMaterials module={module} /> : <div className={styles.panelCard}>
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
            <label htmlFor="lesson-text" className={styles.label}>Lesson text</label>
            <textarea
              className={styles.textarea}
              rows={6}
              id="lesson-text" value={lessonText}
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
              aria-label="YouTube lesson link (optional)" aria-describedby="youtube-feedback" aria-invalid={Boolean(youtubeLink.trim() && !youtubeVideoId(youtubeLink))} value={youtubeLink}
              onChange={(e) => setYoutubeLink(e.target.value)}
              placeholder="YouTube link (optional)"
              disabled={editingDisabled}
            />
          </div>

          <div className={styles.field}>
            <div id="youtube-feedback">{youtubeLink.trim() && !youtubeVideoId(youtubeLink) ? <p role="alert">Please provide a valid YouTube video URL.</p> : <VideoPreview url={youtubeLink} />}</div>
            <label htmlFor="lesson-notes" className={styles.label}>Notes for the AI mentor (optional)</label>
            <textarea
              className={styles.textarea}
              rows={3}
              id="lesson-notes" value={aiNotes}
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
              type="file" aria-label="Upload original lesson files"
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
                    {!isPublished && !workflow.preview && (
                      <button className={styles.removeBtn} disabled={workflow.preview || isBusy} onClick={() => handleDeleteSavedFile(file.id)} aria-label={`Delete ${file.originalFileName}`}>
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
                    {!isPublished && !workflow.preview && (
                      <button className={styles.removeBtn} disabled={workflow.preview || isBusy} onClick={() => removeFile(i)} aria-label={`Remove ${f.name}`}>
                        <X size={14} />
                      </button>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </div>

          {!isPublished && !workflow.preview && (
            <div className={styles.saveRow}>
              <Button onClick={handleSaveMaterials} isLoading={isSaving} disabled={workflow.preview || isBusy || (!!module && !hasUnsavedChanges)}>Save Materials</Button>
            </div>
          )}
        </div>}

        {/* --- Step 2: Generate + review --- */}
        {module && (
          <div className={styles.panelCard}>
            <div className={styles.panelHeader}>
              <span className={styles.panelIcon}><Sparkles size={16} /></span>
              <div>
                <h2 className={styles.panelTitle}>AI-Generated Lesson</h2>
                <p className={styles.panelSubtitle}>{isPublished ? 'Published content available to eligible Students. Expand a section to read it.' : 'Review before approving — this is what students will see.'}</p>
              </div>
            </div>

            {!hasGeneratedContent && !isPublished && !workflow.preview && (
              <div className={styles.generateRow}>
                <Button onClick={handleGenerate} isLoading={isGenerating} disabled={workflow.preview || isBusy || hasUnsavedChanges}>
                  <Sparkles size={15} style={{ marginRight: 6 }} />
                  {aiStatus === 'FAILED' ? 'Retry Generation' : 'Generate Lesson with AI'}
                </Button>
              </div>
            )}

            {hasGeneratedContent && (
              <div>
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

                <GeneratedLessonSections lesson={module} />

                {lessonStatus !== 'APPROVED' && (
                  <div className={styles.aiReviewActions}>
                    <Button onClick={handleApprove} isLoading={isApproving} disabled={workflow.preview || isBusy || hasUnsavedChanges}>
                      <Check size={15} style={{ marginRight: 6 }} />
                      Approve & Publish
                    </Button>
                    <Button variant="secondary" onClick={handleDecline} isLoading={isDeclining} disabled={workflow.preview || isBusy || hasUnsavedChanges}>
                      <X size={15} style={{ marginRight: 6 }} />
                      Decline
                    </Button>
                    <Button variant="secondary" onClick={handleGenerate} isLoading={isGenerating} disabled={workflow.preview || isBusy || hasUnsavedChanges}>
                      <RefreshCw size={15} style={{ marginRight: 6 }} />
                      Regenerate
                    </Button>
                  </div>
                )}
              </div>
            )}
          </div>
        )}
        </LessonWorkspaceTabs>
        </>}
      </main>
    </PageShell>
  );
}
