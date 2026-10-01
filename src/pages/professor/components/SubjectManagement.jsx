import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { BookOpen, Plus, Pencil, Trash2, ChevronDown, ChevronUp, Sparkles, X } from 'lucide-react';
import PageShell from '../../../components/shared/PageShell';
import Button from '../../../components/shared/Button';
import { useToast } from '../../../context/notifications/useToast';
import { PROFESSOR_NAV_ITEMS } from '../../../data/navigationData';
import {
  getSubjects,
  createSubject,
  updateSubject,
  deleteSubject,
  getWeeks,
  addWeek,
} from '../../../services/professor/professorService';
import { useSubjectWeeks } from '../../../hooks/useSubjectWeeks';
import { useRemoteData } from '../../../hooks/useRemoteData';
import styles from '../styles/SubjectManagement.module.css';

// Subject/week CRUD is restricted to professors in the supplied backend.
const YEAR_LEVELS = [
  { value: 'FIRST_YEAR', label: '1st Year' },
  { value: 'SECOND_YEAR', label: '2nd Year' },
];

export default function SubjectManagement() {
  const { showToast } = useToast();
  const navigate = useNavigate();

  const { data: subjects, setData: setSubjects, isLoading, error: loadError, reload: loadSubjects } = useRemoteData(getSubjects, []);
  const [expandedId, setExpandedId] = useState(null);
  const { weeksBySubject, weeksLoading, weekErrors, loadWeeks } = useSubjectWeeks(getWeeks);

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
  const [weekNumber, setWeekNumber] = useState('');
  const [weekTitle, setWeekTitle] = useState('');

  async function refetchSubjects() {
    try {
      setSubjects(await getSubjects());
    } catch {
      showToast('Saved, but the subject list could not refresh. Reload the page.', 'error');
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
    setSubjectModal('create');
    setSubjectCode('');
    setSubjectName('');
    setDescription('');
    setYearLevel('FIRST_YEAR');
    setFormError('');
  }

  function openEditSubject(subject) {
    setSubjectModal({ editing: subject });
    setSubjectCode(subject.subjectCode);
    setSubjectName(subject.subjectName);
    setDescription(subject.description ?? '');
    setYearLevel(subject.yearLevel);
    setFormError('');
  }

  async function handleSaveSubject(e) {
    e.preventDefault();
    if (isSubmitting) return;
    setFormError('');

    if (!subjectCode.trim() || !subjectName.trim()) {
      setFormError('Please fill in the subject code and name.');
      return;
    }

    const payload = { subjectCode: subjectCode.trim(), subjectName: subjectName.trim(), description: description.trim(), yearLevel };

    setIsSubmitting(true);
    try {
      if (subjectModal === 'create') {
        await createSubject(payload);
        showToast('Subject created.', 'success');
      } else {
        await updateSubject(subjectModal.editing.id, payload);
        showToast('Subject updated.', 'success');
      }
      setSubjectModal(null);
      await refetchSubjects();
    } catch (err) {
      console.error(err);
      setFormError('Something went wrong. Please try again.');
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleConfirmDelete() {
    if (isDeleting) return;
    setIsDeleting(true);
    try {
      await deleteSubject(deletingSubject.id);
      showToast('Subject deleted.', 'success');
      setDeletingSubject(null);
      await refetchSubjects();
    } catch (err) {
      console.error(err);
      showToast('Something went wrong. Please try again.', 'error');
    } finally {
      setIsDeleting(false);
    }
  }

  function openAddWeek(subject) {
    setAddingWeekTo(subject);
    setWeekNumber('');
    setWeekTitle('');
    setFormError('');
  }

  async function handleAddWeek(e) {
    e.preventDefault();
    if (isSubmitting) return;
    setFormError('');

    if (!Number.isInteger(Number(weekNumber)) || Number(weekNumber) < 1 || !weekTitle.trim()) {
      setFormError('Please enter a positive whole week number and a title.');
      return;
    }

    setIsSubmitting(true);
    try {
      await addWeek(addingWeekTo.id, { weekNumber: Number(weekNumber), title: weekTitle.trim() });
      showToast('Module added.', 'success');
      setAddingWeekTo(null);
      if (!await loadWeeks(addingWeekTo.id, true)) {
        showToast('Module added, but the week list could not refresh.', 'error');
      }
      setExpandedId(addingWeekTo.id);
    } catch (err) {
      console.error(err);
      setFormError('Something went wrong. Please try again.');
    } finally {
      setIsSubmitting(false);
    }
  }

  function goToLessonEditor(subject, week) {
    navigate(`/professor/subjects/${subject.id}/week/${week.id}`);
  }

  return (
    <PageShell navItems={PROFESSOR_NAV_ITEMS} sectionLabel="Prof" roleBadge="Professor">
      <header className={styles.topbar}>
        <div className={styles.breadcrumb}>
          <BookOpen size={16} />
          Subjects
        </div>
        <Button onClick={openCreateSubject}>
          <Plus size={16} style={{ marginRight: 6 }} />
          Create Subject
        </Button>
      </header>

      <main className={styles.main}>
        {isLoading ? (
          <p className={styles.loadingText}>Loading subjects...</p>
        ) : loadError ? (
          <div style={{ padding: '3rem', textAlign: 'center' }}>
            <p className={styles.loadingText}>Couldn't load subjects. Please check your connection.</p>
            <button
              onClick={loadSubjects}
              style={{ marginTop: '12px', color: 'var(--color-accent-active)', textDecoration: 'underline', background: 'none', border: 'none', cursor: 'pointer' }}
            >
              Retry
            </button>
          </div>
        ) : (
          <div className={styles.subjectsList}>
            {subjects.map((subject) => {
              const isExpanded = expandedId === subject.id;
              const weeks = weeksBySubject[subject.id];
              return (
                <div key={subject.id} className={styles.subjectCard}>
                  <div className={styles.subjectHeader}>
                    <button className={styles.subjectHeaderClickable} onClick={() => toggleExpand(subject.id)}>
                      <BookOpen size={16} className={styles.subjectIcon} />
                      <span className={styles.subjectTitle}>
                        {subject.subjectCode} — {subject.subjectName}
                      </span>
                      {isExpanded ? <ChevronUp size={16} className={styles.chevron} /> : <ChevronDown size={16} className={styles.chevron} />}
                    </button>

                    <div className={styles.subjectActions}>
                      <button className={styles.iconBtn} onClick={() => openEditSubject(subject)} aria-label="Edit subject">
                        <Pencil size={14} />
                      </button>
                      <button
                        className={`${styles.iconBtn} ${styles.iconBtnDanger}`}
                        onClick={() => setDeletingSubject(subject)}
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
                          <span className={styles.weekTitle}>Week {week.weekNumber}: {week.title}</span>
                          <button className={styles.weekEditLink} onClick={() => goToLessonEditor(subject, week)}>
                            Author Lesson
                          </button>
                        </div>
                      ))}

                      <button className={styles.addWeekBtn} onClick={() => openAddWeek(subject)}>
                        <Plus size={14} />
                        Add New Module
                      </button>
                    </div>
                  )}
                </div>
              );
            })}

            {subjects.length === 0 && (
              <p className={styles.emptyState}>No subjects yet. Click "Create Subject" to add one.</p>
            )}
          </div>
        )}
      </main>

      {subjectModal && (
        <div className={styles.modalOverlay} onClick={() => setSubjectModal(null)}>
          <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
            <div className={styles.modalHeader}>
              <h3 className={styles.modalTitle}>{subjectModal === 'create' ? 'Create Subject' : 'Edit Subject'}</h3>
              <button className={styles.closeBtn} onClick={() => setSubjectModal(null)} aria-label="Close">
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleSaveSubject} className={styles.form}>
              <div className={styles.field}>
                <label className={styles.label}>Subject code</label>
                <input
                  type="text"
                  className={styles.input}
                  value={subjectCode}
                  onChange={(e) => setSubjectCode(e.target.value)}
                  placeholder="e.g. HRM101"
                  autoFocus
                />
              </div>

              <div className={styles.field}>
                <label className={styles.label}>Subject name</label>
                <input
                  type="text"
                  className={styles.input}
                  value={subjectName}
                  onChange={(e) => setSubjectName(e.target.value)}
                  placeholder="e.g. Front Office Operations"
                />
              </div>

              <div className={styles.field}>
                <label className={styles.label}>Description</label>
                <textarea
                  className={styles.input}
                  rows={3}
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="Short description of this subject"
                />
              </div>

              <div className={styles.field}>
                <label className={styles.label}>Year level</label>
                <select className={styles.input} value={yearLevel} onChange={(e) => setYearLevel(e.target.value)}>
                  {YEAR_LEVELS.map((yl) => (
                    <option key={yl.value} value={yl.value}>{yl.label}</option>
                  ))}
                </select>
              </div>

              {formError && <p className={styles.formError}>{formError}</p>}

              <Button type="submit" isLoading={isSubmitting}>
                {subjectModal === 'create' ? 'Create Subject' : 'Save Changes'}
              </Button>
            </form>
          </div>
        </div>
      )}

      {deletingSubject && (
        <div className={styles.modalOverlay} onClick={() => setDeletingSubject(null)}>
          <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
            <h3 className={styles.modalTitle}>Delete "{deletingSubject.subjectName}"?</h3>
            <p className={styles.modalWarning}>
              This will also remove all modules inside it. This action cannot be undone.
            </p>
            <div className={styles.modalActions}>
              <Button variant="secondary" onClick={() => setDeletingSubject(null)} disabled={isDeleting}>Cancel</Button>
              <Button variant="danger" onClick={handleConfirmDelete} isLoading={isDeleting}>Delete</Button>
            </div>
          </div>
        </div>
      )}

      {addingWeekTo && (
        <div className={styles.modalOverlay} onClick={() => setAddingWeekTo(null)}>
          <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
            <div className={styles.modalHeader}>
              <h3 className={styles.modalTitle}>Add New Module</h3>
              <button className={styles.closeBtn} onClick={() => setAddingWeekTo(null)} aria-label="Close">
                <X size={18} />
              </button>
            </div>
            <p className={styles.modalSubtitle}>Adding to: {addingWeekTo.subjectName}</p>

            <form onSubmit={handleAddWeek} className={styles.form}>
              <div className={styles.field}>
                <label className={styles.label}>Week number</label>
                <input
                  type="number"
                  min="1"
                  className={styles.input}
                  value={weekNumber}
                  onChange={(e) => setWeekNumber(e.target.value)}
                  placeholder="e.g. 4"
                  autoFocus
                />
              </div>

              <div className={styles.field}>
                <label className={styles.label}>Module title</label>
                <input
                  type="text"
                  className={styles.input}
                  value={weekTitle}
                  onChange={(e) => setWeekTitle(e.target.value)}
                  placeholder="e.g. Guest Relations"
                />
              </div>

              {formError && <p className={styles.formError}>{formError}</p>}

              <Button type="submit" isLoading={isSubmitting}>Add Module</Button>
            </form>
          </div>
        </div>
      )}
    </PageShell>
  );
}
