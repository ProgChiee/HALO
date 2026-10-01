import { useState, useRef, useMemo } from 'react';
import { GraduationCap, Search, Plus, Eye, Pencil, Power, X } from 'lucide-react';
import PageShell from '../../../components/shared/PageShell';
import Button from '../../../components/shared/Button';
import { useToast } from '../../../context/notifications/useToast';
import { useAuth } from '../../../context/login/useAuth';
import { ADMIN_NAV_ITEMS, SUPERADMIN_NAV_ITEMS } from '../../../data/navigationData';
import {
  getProfessors,
  createProfessor,
  toggleProfessorStatus,
  updateProfessor,
} from '../../../services/admin/adminService';
import { useRemoteData } from '../../../hooks/useRemoteData';
import { STATUS_LABELS } from '../../../utils/backendContract';
import styles from '../styles/ProfessorManagement.module.css';

// ⚠️ Adjusted for the real backend's ProfessorResponse: { id, name, email,
// professorId, status } — note the field is "name", not "fullName" (every
// `.fullName` read below was changed to `.name`).
//
// Also: subjectsCount, lessonsCount, uploadedMaterialsCount, and lastLogin
// don't exist on the backend response — those columns now show "—"
// instead of crashing on undefined. Ask your backend team if these should
// be added, or if they're meant to come from a different endpoint.
//
// The create form now includes a Password field — the backend's
// ProfessorRequest requires one upfront (see adminService.js notes: the
// UI copy used to say "they'll receive an invitation to set their
// password", which doesn't match this — worth confirming the intended
// flow with your backend team).

export default function ProfessorManagement() {
  const { showToast } = useToast();
  const { role } = useAuth();
  const isSuperAdmin = role === 'superadmin';

  const { data: professors, setData: setProfessors, isLoading, error: loadError, reload: loadProfessors } = useRemoteData(getProfessors, []);
  const toggleLock = useRef(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [viewingProfessor, setViewingProfessor] = useState(null);
  const [editingProfessor, setEditingProfessor] = useState(null);
  const [togglingId, setTogglingId] = useState(null);

  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [professorId, setProfessorId] = useState('');
  const [formError, setFormError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function refetchProfessors() {
    try {
    const data = await getProfessors();
    setProfessors(data);
    return data;
    } catch {
      showToast("Saved, but the list could not refresh. Reload the page.", 'error');
    }
  }

  const filteredProfessors = useMemo(() => {
    return professors.filter((p) =>
      p.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      p.email.toLowerCase().includes(searchQuery.toLowerCase())
    );
  }, [professors, searchQuery]);

  function openCreateModal() {
    setName('');
    setEmail('');
    setPassword('');
    setProfessorId('');
    setFormError('');
    setShowCreateModal(true);
  }

  async function handleCreate(e) {
    e.preventDefault();
    if (isSubmitting) return;
    setFormError('');

    if (!name || !email || !password || !professorId) {
      setFormError('Please fill in all fields.');
      return;
    }
    if (password.length < 8) {
      setFormError('Password must be at least 8 characters.');
      return;
    }

    setIsSubmitting(true);
    try {
      await createProfessor({ name, email, password, professorId });
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
    if (toggleLock.current) return;
    toggleLock.current = true;
    setTogglingId(professor.id);
    try {
      const updated = await toggleProfessorStatus(professor.id);
      setProfessors((previous) => previous.map((item) => item.id === updated.id ? updated : item));
      setViewingProfessor((previous) => previous?.id === updated.id ? updated : previous);
      showToast('Account status: ' + (STATUS_LABELS[updated.status] ?? updated.status), 'success');
    } catch {
      showToast("Couldn't update the account status. Please try again.", 'error');
    } finally {
      toggleLock.current = false;
      setTogglingId(null);
    }
  }

  function openEditModal(professor) {
    setEditingProfessor(professor);
    setName(professor.name);
    setEmail(professor.email);
    setProfessorId(professor.professorId);
    setFormError('');
  }

  async function handleSaveEdit(e) {
    e.preventDefault();
    if (isSubmitting) return;
    setFormError('');

    if (!name || !email || !professorId) {
      setFormError('Please fill in all fields.');
      return;
    }

    setIsSubmitting(true);
    try {
      await updateProfessor(editingProfessor.id, { name, email, professorId });
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
              <span>Professor ID</span>
              <span>Status</span>
              <span>Actions</span>
            </div>

            {filteredProfessors.map((prof) => (
              <div
                key={prof.id}
                className={`${styles.tableRow} ${styles.tableRowClickable}`}
                onClick={() => setViewingProfessor(prof)}
              >
                <span className={styles.profName}>{prof.name}</span>
                <span className={styles.profEmail}>{prof.email}</span>
                <span className={styles.profCount}>{prof.professorId}</span>
                <span className={`${styles.statusText} ${styles[`status_${prof.status?.toLowerCase()}`]}`}>
                  {STATUS_LABELS[prof.status] ?? prof.status}
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
                    className={`${styles.actionBtn} ${prof.status !== 'ACTIVE' ? styles.actionBtnOff : ''}`}
                    onClick={() => handleToggleStatus(prof)}
                    disabled={togglingId !== null}
                    aria-label={prof.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
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
              Enter the professor's details and set an initial password.
            </p>

            <form onSubmit={handleCreate} className={styles.form}>
              <div className={styles.field}>
                <label className={styles.label}>Fullname</label>
                <input
                  type="text"
                  className={styles.input}
                  value={name}
                  onChange={(e) => setName(e.target.value)}
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

              <div className={styles.field}>
                <label className={styles.label}>Initial password</label>
                <input
                  type="password"
                  className={styles.input}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="At least 8 characters"
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
                  {viewingProfessor.name.replace(/^(Dr\.|Prof\.)\s*/, '').charAt(0)}
                </div>
                <div>
                  <h3 className={styles.modalTitle}>{viewingProfessor.name}</h3>
                  <p className={styles.profileEmail}>{viewingProfessor.email}</p>
                </div>
              </div>
              <button className={styles.closeBtn} onClick={() => setViewingProfessor(null)} aria-label="Close">
                <X size={18} />
              </button>
            </div>

            <div className={styles.detailList}>
              <div className={styles.detailRow}>
                <span className={styles.detailLabel}>Professor ID</span>
                <span className={styles.detailValue}>{viewingProfessor.professorId}</span>
              </div>
              <div className={styles.detailRow}>
                <span className={styles.detailLabel}>Status</span>
                <span className={`${styles.detailValue} ${styles[`status_${viewingProfessor.status?.toLowerCase()}`]}`}>
                  {STATUS_LABELS[viewingProfessor.status] ?? viewingProfessor.status}
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
                  {editingProfessor.name.replace(/^(Dr\.|Prof\.)\s*/, '').charAt(0)}
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
                  value={name}
                  onChange={(e) => setName(e.target.value)}
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