import SubjectPagination from '../../../components/shared/SubjectPagination';
import { useSubjectPagination } from '../../../hooks/useSubjectPagination';
import SubjectCover from '../../../components/shared/SubjectCover';
import SubjectCoverDialog from './SubjectCoverDialog';
import Breadcrumb from '../../../components/shared/Breadcrumb';
import Topbar from '../../../components/shared/Topbar';
import Dialog from '../../../components/shared/Dialog';
import { professorErrorMessage } from '../../../utils/professorErrors';
import { logProfessorError } from '../../../utils/professorDiagnostics';
import { useState, useRef, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Archive, ArchiveRestore, BookOpen, Plus, Pencil, Trash2, ChevronDown, ChevronUp, Sparkles, X } from 'lucide-react';
import PageShell from './ProfessorPageShell';
import Button from '../../../components/shared/Button';
import { useToast } from '../../../context/notifications/useToast';
import { PROFESSOR_NAV_ITEMS } from '../../../data/navigationData';
import { useProfessorWorkflow } from '../../../context/professor/useProfessorWorkflow';
import { useSubjectWeeks } from '../../../hooks/useSubjectWeeks';
import { useRemoteData } from '../../../hooks/useRemoteData';
import styles from '../styles/SubjectManagement.module.css';

// Subject/week CRUD is restricted to professors in the supplied backend.
const YEAR_LEVELS = [
  { value: 'FIRST_YEAR', label: '1st Year' },
  { value: 'SECOND_YEAR', label: '2nd Year' },
];

export default function SubjectManagement() {
  const workflow = useProfessorWorkflow();
  const { getSubjects,
  createSubject,
  updateSubject,
  deleteSubject,
  getWeeks,
  addWeek, updateWeek, } = workflow.api;
  const { showToast } = useToast();
  const navigate = useNavigate();

  const { data: subjects, setData: setSubjects, isLoading, error: loadError, reload: loadSubjects } = useRemoteData(getSubjects, []);
  const [showArchived, setShowArchived] = useState(false);
  const [archiveTarget, setArchiveTarget] = useState(null);
  const visibleSubjects = subjects.filter(subject => Boolean(subject.archivedAt) === showArchived);
  const pagination = useSubjectPagination(visibleSubjects, showArchived);
  const [coverSubject,setCoverSubject]=useState(null);
  const [coverRevisions,setCoverRevisions]=useState({});
  const [expandedId, setExpandedId] = useState(null);
  const { weeksBySubject, weeksLoading, weekErrors, loadWeeks, replaceWeek } = useSubjectWeeks(getWeeks);

  const [subjectModal, setSubjectModal] = useState(null); // null | 'create' | { editing: subject }
  const [subjectCode, setSubjectCode] = useState('');
  const [subjectName, setSubjectName] = useState('');
  const [description, setDescription] = useState('');
  const [yearLevel, setYearLevel] = useState('FIRST_YEAR');
  const [formError, setFormError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const [deletingSubject, setDeletingSubject] = useState(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const [addingWeekTo, setAddingWeekTo] = useState(null);
  const [editingWeek, setEditingWeek] = useState(null);
  const [weekNumber, setWeekNumber] = useState('');
  const [weekTitle, setWeekTitle] = useState('');

  const modalIdentity = useRef(null);
  const pendingOperation = useRef(null);
  const mounted = useRef(false);
  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; modalIdentity.current = null; pendingOperation.current = null; };
  }, []);

  function beginModal() {
    if (workflow.preview) return false;
    if (!mounted.current || pendingOperation.current || isDeleting) return false;
    modalIdentity.current = Symbol('modal');
    setSubjectModal(null);
    setAddingWeekTo(null);
    setEditingWeek(null);
    setDeletingSubject(null);
    return true;
  }

  function closeModal() {
    if (pendingOperation.current) return;
    modalIdentity.current = null;
    setSubjectModal(null);
    setAddingWeekTo(null);
    setEditingWeek(null);
  }

  function startOperation() {
    if (!mounted.current || !modalIdentity.current || pendingOperation.current) return null;
    const operation = { modal: modalIdentity.current };
    pendingOperation.current = operation;
    return () => mounted.current && modalIdentity.current === operation.modal && pendingOperation.current === operation;
  }

  async function refetchSubjects(isCurrent = () => mounted.current) {
    try {
      const updated = await getSubjects();
      if (isCurrent()) setSubjects(updated);
    } catch {
      if (isCurrent()) showToast('Saved, but the subject list could not refresh. Reload the page.', 'error');
    }
  }

  async function toggleExpand(subjectId) {
    if (expandedId === subjectId) {
      setExpandedId(null);
      return;
    }
    setExpandedId(subjectId);
    await loadWeeks(subjectId);
  }

  function openCreateSubject() {
    if (!beginModal()) return;
    setSubjectModal('create');
    setSubjectCode('');
    setSubjectName('');
    setDescription('');
    setYearLevel('FIRST_YEAR');
    setFormError('');
  }

  function openEditSubject(subject) {
    if (!beginModal()) return;
    setSubjectModal({ editing: subject });
    setSubjectCode(subject.subjectCode);
    setSubjectName(subject.subjectName);
    setDescription(subject.description ?? '');
    setYearLevel(subject.yearLevel);
    setFormError('');
  }

  async function handleSaveSubject(e) {
    e.preventDefault();
    if (pendingOperation.current) return;
    setFormError('');

    if (!subjectCode.trim() || !subjectName.trim()) {
      setFormError('Please fill in the subject code and name.');
      return;
    }

    const payload = { subjectCode: subjectCode.trim(), subjectName: subjectName.trim(), description: description.trim(), yearLevel };

    const isCurrent = startOperation();
    if (!isCurrent) return;
    setIsSubmitting(true);
    try {
      if (subjectModal === 'create') {
        await createSubject(payload);
        if (!isCurrent()) return;
        showToast('Subject created.', 'success');
      } else {
        await updateSubject(subjectModal.editing.id, payload);
        if (!isCurrent()) return;
        showToast('Subject updated.', 'success');
      }
      await refetchSubjects(isCurrent);
      if (isCurrent()) setSubjectModal(null);
    } catch (err) {
      if (!isCurrent()) return;
      logProfessorError('save-subject', err);
      setFormError(professorErrorMessage(err));
    } finally {
      if (isCurrent()) { pendingOperation.current = null; setIsSubmitting(false); }
    }
  }

  async function changeArchive() {
    const current = startOperation();
    if (!current) return;
    setIsSubmitting(true);
    try {
      const archive = !archiveTarget.archivedAt;
      await workflow.api.setSubjectArchived(archiveTarget.id, archive);
      if (!current()) return;
      setArchiveTarget(null);
      showToast(archive ? 'Subject archived.' : 'Subject restored.', 'success');
      await refetchSubjects(current);
    } catch (error) {
      if (current()) showToast(professorErrorMessage(error), 'error');
    } finally {
      if (current()) { pendingOperation.current = null; setIsSubmitting(false); }
    }
  }

  async function handleConfirmDelete() {
    if (isDeleting) return;
    setIsDeleting(true);
    try {
      await deleteSubject(deletingSubject.id);
      if (!mounted.current) return;
      showToast('Subject deleted.', 'success');
      setDeletingSubject(null);
      await refetchSubjects();
    } catch (err) {
      if (!mounted.current) return;
      logProfessorError('delete-subject', err);
      showToast(professorErrorMessage(err), 'error');
    } finally {
      if (mounted.current) setIsDeleting(false);
    }
  }

  function openAddWeek(subject) {
    if (!beginModal()) return;
    setAddingWeekTo(subject);
    setWeekNumber('');
    setWeekTitle('');
    setFormError('');
  }

  async function handleAddWeek(e) {
    e.preventDefault();
    if (pendingOperation.current) return;
    setFormError('');

    if (!Number.isInteger(Number(weekNumber)) || Number(weekNumber) < 1 || !weekTitle.trim()) {
      setFormError('Please enter a positive whole week number and a title.');
      return;
    }

    const isCurrent = startOperation();
    if (!isCurrent) return;
    setIsSubmitting(true);
    try {
      await addWeek(addingWeekTo.id, { weekNumber: Number(weekNumber), title: weekTitle.trim() });
      if (!isCurrent()) return;
      showToast('Module added.', 'success');
      const refreshed = await loadWeeks(addingWeekTo.id, true);
      if (!isCurrent()) return;
      setAddingWeekTo(null);
      if (!refreshed) {
        showToast('Module added, but the week list could not refresh.', 'error');
      }
      setExpandedId(addingWeekTo.id);
    } catch (err) {
      if (!isCurrent()) return;
      logProfessorError('create-week', err);
      setFormError(professorErrorMessage(err));
    } finally {
      if (isCurrent()) { pendingOperation.current = null; setIsSubmitting(false); }
    }
  }

  function openEditWeek(subject, week) {
    if (!beginModal()) return;
    setEditingWeek({ subject, week });
    setWeekTitle(week.title ?? '');
    setFormError('');
  }

  async function handleSaveTitle(e) {
    e.preventDefault();
    if (pendingOperation.current) return;
    const title = weekTitle.trim();
    if (!title || title.length > 255) {
      setFormError('Enter a module title between 1 and 255 characters.');
      return;
    }
    const isCurrent = startOperation();
    if (!isCurrent) return;
    setIsSubmitting(true);
    setFormError('');
    try {
      const saved = await updateWeek(editingWeek.week.id, { weekNumber: editingWeek.week.weekNumber, title });
      if (!isCurrent()) return;
      replaceWeek(editingWeek.subject.id, saved);
      setEditingWeek(null);
      showToast('Module title updated.', 'success');
    } catch (err) {
      if (!isCurrent()) return;
      logProfessorError('update-week', err);
      setFormError(professorErrorMessage(err));
    } finally {
      if (isCurrent()) { pendingOperation.current = null; setIsSubmitting(false); }
    }
  }

  function goToLessonEditor(subject, week) {
    navigate(`${workflow.basePath}/subjects/${subject.id}/week/${week.id}`, {
      state: { subjectName: subject.subjectName, weekNumber: week.weekNumber, lessonTitle: week.title }
    });
  }

  return (
    <PageShell responsive navItems={PROFESSOR_NAV_ITEMS} sectionLabel="Prof" roleBadge="Professor">
      <Topbar>
        <Breadcrumb items={[{ label: 'Dashboard', to: workflow.basePath }, { label: 'Subjects' }]} />
        <Button onClick={openCreateSubject} disabled={isSubmitting || workflow.preview}>
          <Plus size={16} style={{ marginRight: 6 }} />
          Create Subject
        </Button>
      </Topbar>

      <main className={styles.main}>
        {isLoading ? (
          <p className={styles.loadingText}>Loading subjects...</p>
        ) : loadError ? (
          <div style={{ padding: 'var(--space-12)', textAlign: 'center' }}>
            <p className={styles.loadingText}>{professorErrorMessage(loadError, "Couldn't load subjects. Please try again.")}</p>
            <button
              onClick={loadSubjects}
              style={{ marginTop: '12px', color: 'var(--color-accent-active)', textDecoration: 'underline', background: 'none', border: 'none', cursor: 'pointer' }}
            >
              Retry
            </button>
          </div>
        ) : (
          <>
            <div className={styles.statusTabs} role="tablist" aria-label="Subject status">
              {['Active Subjects', 'Archived Subjects'].map((label, index) => {
                const archived = index === 1;
                const selected = showArchived === archived;
                return (
                  <button key={label} type="button" role="tab" id={archived ? 'archived-subjects-tab' : 'active-subjects-tab'}
                    aria-selected={selected} aria-controls="subject-results" tabIndex={selected ? 0 : -1}
                    onClick={() => setShowArchived(archived)}
                    onKeyDown={event => {
                      const positions = { ArrowRight: 1 - index, ArrowLeft: 1 - index, Home: 0, End: 1 };
                      if (!Object.hasOwn(positions, event.key)) return;
                      event.preventDefault();
                      event.currentTarget.parentElement.children[positions[event.key]].focus();
                      setShowArchived(positions[event.key] === 1);
                    }}>{label}</button>
                );
              })}
            </div>
          <div className={styles.subjectsList} role="tabpanel" id="subject-results" tabIndex={0}
            aria-labelledby={showArchived ? 'archived-subjects-tab' : 'active-subjects-tab'}>
            {pagination.items.map((subject) => {
              const isExpanded = expandedId === subject.id;
              const weeks = weeksBySubject[subject.id];
              return (
                <div key={subject.id} className={styles.subjectCard}>
                  <SubjectCover subjectId={subject.id} load={subject.coverUrl ? workflow.api.getSubjectCover : undefined} revision={coverRevisions[subject.id] ?? 0} />
                  <div className={styles.subjectHeader}>
                    <button className={styles.subjectHeaderClickable} aria-expanded={isExpanded} onClick={() => toggleExpand(subject.id)}>
                      <BookOpen size={16} className={styles.subjectIcon} />
                      <span className={styles.subjectTitle}>
                        <span className={styles.cardLabel}>{subject.subjectCode}</span>
                        {subject.subjectName}
                      </span>
                      {isExpanded ? <ChevronUp size={16} className={styles.chevron} /> : <ChevronDown size={16} className={styles.chevron} />}
                    </button>

                    <div className={styles.subjectActions}>
                      {!workflow.preview && <button type="button" className={styles.coverAction} disabled={isSubmitting}
                        onClick={() => { if (beginModal()) setArchiveTarget(subject); }}>
                        {subject.archivedAt ? <ArchiveRestore size={16} aria-hidden="true" /> : <Archive size={16} aria-hidden="true" />}
                        {subject.archivedAt ? 'Restore' : 'Archive'}</button>}
                      {!workflow.preview && !subject.archivedAt && <button type="button" className={styles.coverAction} disabled={isSubmitting} onClick={()=>setCoverSubject(subject)}>Change cover</button>}
                      <button className={styles.iconBtn} disabled={isSubmitting || workflow.preview || Boolean(subject.archivedAt)} onClick={() => openEditSubject(subject)} aria-label="Edit subject">
                        <Pencil size={14} />
                      </button>
                      <button
                        className={`${styles.iconBtn} ${styles.iconBtnDanger}`}
                        disabled={isSubmitting || workflow.preview} onClick={() => { if (beginModal()) setDeletingSubject(subject); }}
                        aria-label="Delete subject"
                      >
                        <Trash2 size={14} />
                      </button>
                    </div>
                  </div>

                  {isExpanded && (
                    <div className={styles.weeksPanel}>
                      {weeksLoading[subject.id] && !weeks && <p className={styles.noWeeksText}>Loading modules...</p>}
                      {weekErrors[subject.id] && <p className={styles.noWeeksText}>Couldn't load weeks. Collapse and reopen to retry.</p>}
                      {weeks?.length === 0 && <p className={styles.noWeeksText}>No modules yet.</p>}

                      {weeks?.map((week) => (
                        <div key={week.id} className={styles.weekRow}>
                          <Sparkles size={14} className={styles.weekIcon} />
                          <span className={styles.weekTitle}><span className={styles.cardLabel}>Week {week.weekNumber}</span>{week.title}</span>
                          <div className={styles.weekActions}>
                          <button className={styles.weekEditLink} disabled={isSubmitting || workflow.preview || Boolean(subject.archivedAt)} onClick={() => openEditWeek(subject, week)} aria-label={`Edit module title: ${week.title}`}>
                            Edit title
                          </button>
                          <button className={styles.weekEditLink} disabled={Boolean(subject.archivedAt)} onClick={() => goToLessonEditor(subject, week)}>
                            {workflow.preview ? "View Lesson" : "Author Lesson"}
                          </button>
                          </div>
                        </div>
                      ))}

                      <button className={styles.addWeekBtn} disabled={isSubmitting || workflow.preview || Boolean(subject.archivedAt)} onClick={() => openAddWeek(subject)}>
                        <Plus size={14} />
                        Add New Module
                      </button>
                    </div>
                  )}
                </div>
              );
            })}

            {visibleSubjects.length === 0 && (
              <p className={styles.emptyState}>{showArchived ? "No archived subjects." : workflow.preview ? "No subjects yet." : 'No subjects yet. Click "Create Subject" to add one.'}</p>
            )}
          </div>
          {pagination.total > 0 && <SubjectPagination compact pagination={pagination} label="Subject pages" />}
          </>
        )}
      </main>

      {coverSubject && !workflow.preview && <SubjectCoverDialog subject={coverSubject} api={workflow.api} onClose={()=>setCoverSubject(null)} onSaved={()=>{setCoverRevisions(old=>({...old,[coverSubject.id]:(old[coverSubject.id]??0)+1}));setCoverSubject(null);showToast('Subject cover updated.','success');}} />}
      {subjectModal && (
        <Dialog labelledBy="subject-dialog" busy={isSubmitting} onClose={closeModal} className={styles.modal}>
            <div className={styles.modalHeader}>
              <h3 id="subject-dialog" className={styles.modalTitle}>{subjectModal === 'create' ? 'Create Subject' : 'Edit Subject'}</h3>
              <button className={styles.closeBtn} disabled={isSubmitting || workflow.preview} onClick={closeModal} aria-label="Close">
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleSaveSubject} className={styles.form}>
              <div className={styles.field}>
                <label htmlFor="subject-code" className={styles.label}>Subject code</label>
                <input
                  type="text"
                  className={styles.input}
                  id="subject-code" aria-describedby={formError ? 'academic-form-error' : undefined} value={subjectCode}
                  onChange={(e) => setSubjectCode(e.target.value)}
                  placeholder="e.g. HRM101"
                />
              </div>

              <div className={styles.field}>
                <label htmlFor="subject-name" className={styles.label}>Subject name</label>
                <input
                  type="text"
                  className={styles.input}
                  id="subject-name" aria-describedby={formError ? 'academic-form-error' : undefined} value={subjectName}
                  onChange={(e) => setSubjectName(e.target.value)}
                  placeholder="e.g. Front Office Operations"
                />
              </div>

              <div className={styles.field}>
                <label htmlFor="subject-description" className={styles.label}>Description</label>
                <textarea
                  className={styles.input}
                  rows={3}
                  id="subject-description" aria-describedby={formError ? 'academic-form-error' : undefined} value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="Short description of this subject"
                />
              </div>

              <div className={styles.field}>
                <label htmlFor="subject-year" className={styles.label}>Year level</label>
                <select className={styles.input} id="subject-year" aria-describedby={formError ? 'academic-form-error' : undefined} value={yearLevel} onChange={(e) => setYearLevel(e.target.value)}>
                  {YEAR_LEVELS.map((yl) => (
                    <option key={yl.value} value={yl.value}>{yl.label}</option>
                  ))}
                </select>
              </div>

              {formError && <p id="academic-form-error" role="alert" className={styles.formError}>{formError}</p>}

              <Button type="submit" isLoading={isSubmitting}>
                {subjectModal === 'create' ? 'Create Subject' : 'Save Changes'}
              </Button>
            </form>
        </Dialog>
      )}

      {archiveTarget && (
        <Dialog labelledBy="archive-subject-dialog" busy={isSubmitting} onClose={() => { if (!isSubmitting) setArchiveTarget(null); }} className={styles.modal}>
          <h3 id="archive-subject-dialog">{archiveTarget.archivedAt ? 'Restore' : 'Archive'} {archiveTarget.subjectName}?</h3>
          <p>{archiveTarget.archivedAt ? 'Editing will reopen. Eligible students can enroll when published lessons and an active Professor are available.' : 'Enrollment and editing will close. Enrolled students keep access to published lessons and their learning history.'}</p>
          <div className={styles.modalActions}>
            <Button variant="secondary" disabled={isSubmitting} onClick={() => setArchiveTarget(null)}>Cancel</Button>
            <Button isLoading={isSubmitting} onClick={changeArchive}>{archiveTarget.archivedAt ? 'Restore' : 'Archive'}</Button>
          </div>
        </Dialog>
      )}
      {deletingSubject && (
        <Dialog labelledBy="delete-subject-dialog" busy={isDeleting} onClose={() => { if (!isDeleting) setDeletingSubject(null); }} className={styles.modal} initialFocus="button">
            <h3 id="delete-subject-dialog" className={styles.modalTitle}>Delete "{deletingSubject.subjectName}"?</h3>
            <p className={styles.modalWarning}>
              Only an empty subject with no enrollment history can be deleted. Deleting an empty subject cannot be undone.
            </p>
            <div className={styles.modalActions}>
              <Button variant="secondary" onClick={() => setDeletingSubject(null)} disabled={isDeleting}>Cancel</Button>
              <Button variant="danger" onClick={handleConfirmDelete} isLoading={isDeleting}>Delete</Button>
            </div>
        </Dialog>
      )}

      {editingWeek && (
        <Dialog labelledBy="edit-module-title" busy={isSubmitting} onClose={closeModal} className={styles.modal}>
          <div className={styles.modalHeader}>
            <h3 id="edit-module-title" className={styles.modalTitle}>Edit Module Title</h3>
            <button className={styles.closeBtn} disabled={isSubmitting || workflow.preview} onClick={closeModal} aria-label="Close"><X size={18} /></button>
          </div>
          <p className={styles.modalSubtitle}>{editingWeek.subject.subjectName} · Week {editingWeek.week.weekNumber}</p>
          <form onSubmit={handleSaveTitle} className={styles.form}>
            <div className={styles.field}>
              <label htmlFor="module-title" className={styles.label}>Module title</label>
              <input id="module-title" className={styles.input} value={weekTitle} onChange={e => setWeekTitle(e.target.value)} maxLength={255} disabled={isSubmitting || workflow.preview} aria-invalid={Boolean(formError)} aria-describedby={formError ? 'module-title-error' : undefined} />
            </div>
            {formError && <p id="module-title-error" role="alert" className={styles.formError}>{formError}</p>}
            <div className={styles.modalActions}>
              <Button variant="secondary" onClick={closeModal} disabled={isSubmitting || workflow.preview}>Cancel</Button>
              <Button type="submit" isLoading={isSubmitting}>Save</Button>
            </div>
          </form>
        </Dialog>
      )}

      {addingWeekTo && (
        <Dialog labelledBy="week-dialog" busy={isSubmitting} onClose={closeModal} className={styles.modal}>
            <div className={styles.modalHeader}>
              <h3 id="week-dialog" className={styles.modalTitle}>Add New Module</h3>
              <button className={styles.closeBtn} disabled={isSubmitting || workflow.preview} onClick={closeModal} aria-label="Close">
                <X size={18} />
              </button>
            </div>
            <p className={styles.modalSubtitle}>Adding to: {addingWeekTo.subjectName}</p>

            <form onSubmit={handleAddWeek} className={styles.form}>
              <div className={styles.field}>
                <label htmlFor="week-number" className={styles.label}>Week number</label>
                <input
                  type="number"
                  min="1"
                  className={styles.input}
                  id="week-number" aria-describedby={formError ? 'academic-form-error' : undefined} value={weekNumber}
                  onChange={(e) => setWeekNumber(e.target.value)}
                  placeholder="e.g. 4"
                />
              </div>

              <div className={styles.field}>
                <label htmlFor="week-title" className={styles.label}>Module title</label>
                <input
                  type="text"
                  className={styles.input}
                  id="week-title" aria-describedby={formError ? 'academic-form-error' : undefined} value={weekTitle}
                  onChange={(e) => setWeekTitle(e.target.value)}
                  placeholder="e.g. Guest Relations"
                />
              </div>

              {formError && <p id="academic-form-error" role="alert" className={styles.formError}>{formError}</p>}

              <Button type="submit" isLoading={isSubmitting}>Add Module</Button>
            </form>
        </Dialog>
      )}
    </PageShell>
  );
}
