import { useState, useEffect, useMemo } from 'react';
import { GraduationCap, Search, Plus, Eye, Pencil, Power, X } from 'lucide-react';
import PageShell from '../../../components/shared/PageShell';
import Button from '../../../components/shared/Button';
import { useToast } from '../../../context/notifications/ToastContext';
import { useAuth } from '../../../context/login/AuthContext';
import { ADMIN_NAV_ITEMS, SUPERADMIN_NAV_ITEMS } from '../../../data/navigationData';
import {
  getProfessors,
  createProfessor,
  toggleProfessorStatus,
  updateProfessor,
} from '../../../services/admin/adminService';
import styles from '../styles/ProfessorManagement.module.css';

export default function ProfessorManagement() {
  const { showToast } = useToast();
  const { role } = useAuth();
  const isSuperAdmin = role === 'superadmin';

  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [professors, setProfessors] = useState([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [viewingProfessor, setViewingProfessor] = useState(null);
  const [editingProfessor, setEditingProfessor] = useState(null);
  const [togglingId, setTogglingId] = useState(null);

  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [professorId, setProfessorId] = useState('');
  const [formError, setFormError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    loadProfessors();
  }, []);

  // Used once on mount — shows the "Loading professors..." state.
  async function loadProfessors() {
    setIsLoading(true);
    setLoadError(false);
    try {
      await refetchProfessors();
    } catch (err) {
      console.error(err);
      setLoadError(true);
    } finally {
      setIsLoading(false);
    }
  }

  // Used after actions (create, edit, toggle status) — updates the table
  // data in place without hiding it behind the loading state. Returns the
  // fresh data so callers can sync other state (e.g. an open view modal)
  // with the real result.
  async function refetchProfessors() {
    const data = await getProfessors();
    setProfessors(data);
    return data;
  }

  const filteredProfessors = useMemo(() => {
    return professors.filter((p) =>
      p.fullName.toLowerCase().includes(searchQuery.toLowerCase()) ||
      p.email.toLowerCase().includes(searchQuery.toLowerCase())
    );
  }, [professors, searchQuery]);

  function openCreateModal() {
    setFullName('');
    setEmail('');
    setProfessorId('');
    setFormError('');
    setShowCreateModal(true);
  }

  async function handleCreate(e) {
    e.preventDefault();
    if (isSubmitting) return;
    setFormError('');

    if (!fullName || !email || !professorId) {
      setFormError('Please fill in all fields.');
      return;
    }

    setIsSubmitting(true);
    try {
      await createProfessor({ fullName, email, professorId });
      showToast('Professor account created.', 'success');
      setShowCreateModal(false);
      await refetchProfessors();
    } catch (err) {
      console.error(err);
      setFormError('Something went wrong. Please try again.');
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleToggleStatus(professor) {
    if (togglingId === professor.id) return;

    setTogglingId(professor.id);
    try {
      await toggleProfessorStatus(professor.id);
      showToast(
        professor.status === 'active' ? 'Professor deactivated.' : 'Professor activated.',
        'success'
      );
      const updated = await refetchProfessors();
      const freshProfessor = updated.find((p) => p.id === professor.id);
      setViewingProfessor((prev) =>
        prev && prev.id === professor.id ? freshProfessor : prev
      );
    } catch (err) {
      console.error(err);
      showToast('Something went wrong. Please try again.', 'error');
    } finally {
      setTogglingId(null);
    }
  }

  function openEditModal(professor) {
    setEditingProfessor(professor);
    setFullName(professor.fullName);
    setEmail(professor.email);
    setProfessorId(professor.professorId);
    setFormError('');
  }

  async function handleSaveEdit(e) {
    e.preventDefault();
    if (isSubmitting) return;
    setFormError('');

    if (!fullName || !email || !professorId) {
      setFormError('Please fill in all fields.');
      return;
    }

    setIsSubmitting(true);
    try {
      await updateProfessor(editingProfessor.id, { fullName, email, professorId });
      showToast('Professor info updated.', 'success');
      setEditingProfessor(null);
      await refetchProfessors();
    } catch (err) {
      console.error(err);
      setFormError('Something went wrong. Please try again.');
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <PageShell
      navItems={isSuperAdmin ? SUPERADMIN_NAV_ITEMS : ADMIN_NAV_ITEMS}
      sectionLabel={isSuperAdmin ? 'Superadmin' : 'Admin'}
      roleBadge={isSuperAdmin ? 'Superadmin' : 'Admin'}
    >
      <header className={styles.topbar}>
        <div className={styles.breadcrumb}>
          <GraduationCap size={16} />
          Professor Management
        </div>
      </header>

      <main className={styles.main}>
        <div className={styles.toolbarRow}>
          <div className={styles.searchWrapper}>
            <Search size={18} className={styles.searchIcon} />
            <input
              type="text"
              className={styles.searchInput}
              placeholder="Search professors"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </div>
          <Button onClick={openCreateModal}>
            <Plus size={16} style={{ marginRight: 6 }} />
            Create Professor
          </Button>
        </div>

        {isLoading ? (
          <p className={styles.loadingText}>Loading professors...</p>
        ) : loadError ? (
          <div className={styles.tableCard} style={{ padding: '3rem', textAlign: 'center' }}>
            <p className={styles.loadingText}>Couldn't load professors. Please check your connection.</p>
            <button
              onClick={loadProfessors}
              style={{ marginTop: '12px', color: 'var(--color-accent-active)', textDecoration: 'underline', background: 'none', border: 'none', cursor: 'pointer' }}
            >
              Retry
            </button>
          </div>
        ) : (
          <div className={styles.tableCard}>
            <div className={styles.tableHeaderRow}>
              <span>Name</span>
              <span>Email</span>
              <span>Subjects</span>
              <span>Lessons</span>
              <span>Last login</span>
              <span>Actions</span>
            </div>

            {filteredProfessors.map((prof) => (
              <div
                key={prof.id}
                className={`${styles.tableRow} ${styles.tableRowClickable}`}
                onClick={() => setViewingProfessor(prof)}
              >
                <span className={styles.profName}>{prof.fullName}</span>
                <span className={styles.profEmail}>{prof.email}</span>
                <span className={styles.profCount}>{prof.subjectsCount}</span>
                <span className={styles.profCount}>{prof.lessonsCount}</span>
                <span className={styles.profLastLogin}>
                  {prof.lastLogin}{' '}
                  <span className={`${styles.statusText} ${styles[`status_${prof.status}`]}`}>
                    {prof.status === 'active' ? 'Active' : 'Inactive'}
                  </span>
                </span>
                <span className={styles.actions} onClick={(e) => e.stopPropagation()}>
                  <button
                    className={styles.actionBtn}
                    onClick={() => setViewingProfessor(prof)}
                    aria-label="View"
                  >
                    <Eye size={15} />
                  </button>
                  <button
                    className={styles.actionBtn}
                    onClick={() => openEditModal(prof)}
                    aria-label="Edit"
                  >
                    <Pencil size={15} />
                  </button>
                  <button
                    className={`${styles.actionBtn} ${prof.status === 'inactive' ? styles.actionBtnOff : ''}`}
                    onClick={() => handleToggleStatus(prof)}
                    disabled={togglingId === prof.id}
                    aria-label={prof.status === 'active' ? 'Deactivate' : 'Activate'}
                  >
                    <Power size={15} />
                  </button>
                </span>
              </div>
            ))}

            {filteredProfessors.length === 0 && (
              <p className={styles.emptyState}>No professors match your search.</p>
            )}
          </div>
        )}
      </main>

      {showCreateModal && (
        <div className={styles.modalOverlay} onClick={() => setShowCreateModal(false)}>
          <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
            <div className={styles.modalHeader}>
              <h3 className={styles.modalTitle}>Create Professor Account</h3>
              <button className={styles.closeBtn} onClick={() => setShowCreateModal(false)} aria-label="Close">
                <X size={18} />
              </button>
            </div>
            <p className={styles.modalSubtitle}>
              Enter the professor's name and email address. They will receive an invitation to set their password.
            </p>

            <form onSubmit={handleCreate} className={styles.form}>
              <div className={styles.field}>
                <label className={styles.label}>Fullname</label>
                <input
                  type="text"
                  className={styles.input}
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                />
              </div>

              <div className={styles.field}>
                <label className={styles.label}>Email</label>
                <input
                  type="email"
                  className={styles.input}
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                />
              </div>

              <div className={styles.field}>
                <label className={styles.label}>Professor ID</label>
                <input
                  type="text"
                  className={styles.input}
                  value={professorId}
                  onChange={(e) => setProfessorId(e.target.value)}
                />
              </div>

              {formError && <p className={styles.formError}>{formError}</p>}

              <Button type="submit" isLoading={isSubmitting}>Create Professor</Button>
            </form>
          </div>
        </div>
      )}

      {viewingProfessor && (
        <div className={styles.modalOverlay} onClick={() => setViewingProfessor(null)}>
          <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
            <div className={styles.modalHeader}>
              <div className={styles.profileHeaderRow}>
                <div className={styles.profileAvatar}>
                  {viewingProfessor.fullName.replace(/^(Dr\.|Prof\.)\s*/, '').charAt(0)}
                </div>
                <div>
                  <h3 className={styles.modalTitle}>{viewingProfessor.fullName}</h3>
                  <p className={styles.profileEmail}>{viewingProfessor.email}</p>
                </div>
              </div>
              <button className={styles.closeBtn} onClick={() => setViewingProfessor(null)} aria-label="Close">
                <X size={18} />
              </button>
            </div>

            <div className={styles.detailList}>
              <div className={styles.detailRow}>
                <span className={styles.detailLabel}>Created Subjects</span>
                <span className={styles.detailValue}>{viewingProfessor.subjectsCount}</span>
              </div>
              <div className={styles.detailRow}>
                <span className={styles.detailLabel}>Uploaded Lessons/PDFs</span>
                <span className={styles.detailValue}>{viewingProfessor.uploadedMaterialsCount}</span>
              </div>
              <div className={styles.detailRow}>
                <span className={styles.detailLabel}>Last Login / Activity</span>
                <span className={styles.detailValue}>{viewingProfessor.lastLogin}</span>
              </div>
              <div className={styles.detailRow}>
                <span className={styles.detailLabel}>Status</span>
                <span className={`${styles.detailValue} ${styles[`status_${viewingProfessor.status}`]}`}>
                  {viewingProfessor.status === 'active' ? 'Active' : 'Inactive'}
                </span>
              </div>
            </div>
          </div>
        </div>
      )}

      {editingProfessor && (
        <div className={styles.modalOverlay} onClick={() => setEditingProfessor(null)}>
          <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
            <div className={styles.modalHeader}>
              <div className={styles.profileHeaderRow}>
                <div className={styles.profileAvatar}>
                  {editingProfessor.fullName.replace(/^(Dr\.|Prof\.)\s*/, '').charAt(0)}
                </div>
                <div>
                  <h3 className={styles.modalTitle}>Edit Professor Account</h3>
                  <p className={styles.profileEmail}>{editingProfessor.email}</p>
                </div>
              </div>
              <button className={styles.closeBtn} onClick={() => setEditingProfessor(null)} aria-label="Close">
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleSaveEdit} className={styles.form}>
              <div className={styles.field}>
                <label className={styles.label}>Fullname</label>
                <input
                  type="text"
                  className={styles.input}
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                />
              </div>

              <div className={styles.field}>
                <label className={styles.label}>Email</label>
                <input
                  type="email"
                  className={styles.input}
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                />
              </div>

              <div className={styles.field}>
                <label className={styles.label}>Professor ID</label>
                <input
                  type="text"
                  className={styles.input}
                  value={professorId}
                  onChange={(e) => setProfessorId(e.target.value)}
                />
              </div>

              {formError && <p className={styles.formError}>{formError}</p>}

              <Button type="submit" isLoading={isSubmitting}>Save Changes</Button>
            </form>
          </div>
        </div>
      )}
    </PageShell>
  );
}