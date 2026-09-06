import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  BookOpen,
  Plus,
  Pencil,
  Trash2,
  ChevronDown,
  ChevronUp,
  Folder,
  X,
} from 'lucide-react';
import PageShell from '../../../components/shared/PageShell';
import Button from '../../../components/shared/Button';
import { useToast } from '../../../context/notifications/ToastContext';
import { PROFESSOR_NAV_ITEMS } from '../../../data/navigationData';
import {
  getManagedSubjects,
  createSubject,
  updateSubject,
  deleteSubject,
  addWeek,
} from '../../../services/professor/professorService';
import styles from '../styles/SubjectManagement.module.css';

export default function SubjectManagement() {
  const { showToast } = useToast();
  const navigate = useNavigate();

  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [subjects, setSubjects] = useState([]);
  const [expandedId, setExpandedId] = useState(null);

  // Create/Edit Subject modal
  const [subjectModal, setSubjectModal] = useState(null); // null | 'create' | { editing: subject }
  const [subjectTitle, setSubjectTitle] = useState('');
  const [formError, setFormError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Delete confirm modal
  const [deletingSubject, setDeletingSubject] = useState(null);
  const [isDeleting, setIsDeleting] = useState(false);

  // Add Module modal
  const [addingWeekTo, setAddingWeekTo] = useState(null); // subject or null
  const [weekTitle, setWeekTitle] = useState('');

  useEffect(() => {
    loadSubjects();
  }, []);

  // Used once on mount — shows the "Loading subjects..." state.
  async function loadSubjects() {
    setIsLoading(true);
    setLoadError(false);
    try {
      await refetchSubjects();
    } catch (err) {
      console.error(err);
      setLoadError(true);
    } finally {
      setIsLoading(false);
    }
  }

  // Used after actions (create/edit/delete subject, add module) — updates
  // the list in place without hiding it (and collapsing expanded cards)
  // behind the loading state.
  async function refetchSubjects() {
    const data = await getManagedSubjects();
    setSubjects(data);
  }

  function toggleExpand(subjectId) {
    setExpandedId((prev) => (prev === subjectId ? null : subjectId));
  }

  function openCreateSubject() {
    setSubjectModal('create');
    setSubjectTitle('');
    setFormError('');
  }

  function openEditSubject(subject) {
    setSubjectModal({ editing: subject });
    setSubjectTitle(subject.title);
    setFormError('');
  }

  async function handleSaveSubject(e) {
    e.preventDefault();
    if (isSubmitting) return;
    setFormError('');

    if (!subjectTitle.trim()) {
      setFormError('Please enter a subject title.');
      return;
    }

    setIsSubmitting(true);
    try {
      if (subjectModal === 'create') {
        await createSubject(subjectTitle.trim());
        showToast('Subject created.', 'success');
      } else {
        await updateSubject(subjectModal.editing.id, subjectTitle.trim());
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
    setWeekTitle('');
    setFormError('');
  }

  async function handleAddWeek(e) {
    e.preventDefault();
    if (isSubmitting) return;
    setFormError('');

    if (!weekTitle.trim()) {
      setFormError('Please enter a module title.');
      return;
    }

    setIsSubmitting(true);
    try {
      await addWeek(addingWeekTo.id, weekTitle.trim());
      showToast('Module added.', 'success');
      setAddingWeekTo(null);
      setExpandedId(addingWeekTo.id);
      await refetchSubjects();
    } catch (err) {
      console.error(err);
      setFormError('Something went wrong. Please try again.');
    } finally {
      setIsSubmitting(false);
    }
  }

  function goToWeekEditor(subject, week) {
    navigate(`/professor/subjects/${subject.id}/week/${week.id}`);
  }

  return (
    <PageShell navItems={PROFESSOR_NAV_ITEMS} sectionLabel="Prof" roleBadge="Professor">
      <header className={styles.topbar}>
        <div className={styles.breadcrumb}>
          <BookOpen size={16} />
          Subject Management
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
              return (
                <div key={subject.id} className={styles.subjectCard}>
                  <div className={styles.subjectHeader}>
                    <button
                      className={styles.subjectHeaderClickable}
                      onClick={() => toggleExpand(subject.id)}
                    >
                      <BookOpen size={16} className={styles.subjectIcon} />
                      <span className={styles.subjectTitle}>{subject.title}</span>
                      {isExpanded
                        ? <ChevronUp size={16} className={styles.chevron} />
                        : <ChevronDown size={16} className={styles.chevron} />}
                    </button>

                    <div className={styles.subjectActions}>
                      <button
                        className={styles.iconBtn}
                        onClick={() => openEditSubject(subject)}
                        aria-label="Edit subject"
                      >
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
                      {subject.weeks.length === 0 && (
                        <p className={styles.noWeeksText}>No modules yet.</p>
                      )}

                      {subject.weeks.map((week) => (
                        <div key={week.id} className={styles.weekRow}>
                          <Folder size={14} className={styles.weekIcon} />
                          <span className={styles.weekTitle}>{week.title}</span>
                          <button
                            className={styles.weekEditLink}
                            onClick={() => goToWeekEditor(subject, week)}
                          >
                            Edit
                          </button>
                        </div>
                      ))}

                      <button
                        className={styles.addWeekBtn}
                        onClick={() => openAddWeek(subject)}
                      >
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

      {/* Create / Edit Subject modal */}
      {subjectModal && (
        <div className={styles.modalOverlay} onClick={() => setSubjectModal(null)}>
          <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
            <div className={styles.modalHeader}>
              <h3 className={styles.modalTitle}>
                {subjectModal === 'create' ? 'Create Subject' : 'Edit Subject'}
              </h3>
              <button className={styles.closeBtn} onClick={() => setSubjectModal(null)} aria-label="Close">
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleSaveSubject} className={styles.form}>
              <div className={styles.field}>
                <label className={styles.label}>Subject title</label>
                <input
                  type="text"
                  className={styles.input}
                  value={subjectTitle}
                  onChange={(e) => setSubjectTitle(e.target.value)}
                  placeholder="e.g. Front Office Operations"
                  autoFocus
                />
              </div>

              {formError && <p className={styles.formError}>{formError}</p>}

              <Button type="submit" isLoading={isSubmitting}>
                {subjectModal === 'create' ? 'Create Subject' : 'Save Changes'}
              </Button>
            </form>
          </div>
        </div>
      )}

      {/* Delete confirmation modal */}
      {deletingSubject && (
        <div className={styles.modalOverlay} onClick={() => setDeletingSubject(null)}>
          <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
            <h3 className={styles.modalTitle}>Delete "{deletingSubject.title}"?</h3>
            <p className={styles.modalWarning}>
              This will also remove all {deletingSubject.weeks.length} module(s) inside it. This action cannot be undone.
            </p>
            <div className={styles.modalActions}>
              <Button variant="secondary" onClick={() => setDeletingSubject(null)} disabled={isDeleting}>Cancel</Button>
              <Button variant="danger" onClick={handleConfirmDelete} isLoading={isDeleting}>Delete</Button>
            </div>
          </div>
        </div>
      )}

      {/* Add New Module modal */}
      {addingWeekTo && (
        <div className={styles.modalOverlay} onClick={() => setAddingWeekTo(null)}>
          <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
            <div className={styles.modalHeader}>
              <h3 className={styles.modalTitle}>Add New Module</h3>
              <button className={styles.closeBtn} onClick={() => setAddingWeekTo(null)} aria-label="Close">
                <X size={18} />
              </button>
            </div>
            <p className={styles.modalSubtitle}>Adding to: {addingWeekTo.title}</p>

            <form onSubmit={handleAddWeek} className={styles.form}>
              <div className={styles.field}>
                <label className={styles.label}>Module title</label>
                <input
                  type="text"
                  className={styles.input}
                  value={weekTitle}
                  onChange={(e) => setWeekTitle(e.target.value)}
                  placeholder="e.g. Week 4: Guest Relations"
                  autoFocus
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