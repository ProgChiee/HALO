import Dialog from '../../../components/shared/Dialog';
import { accountText, accountDisplay, accountInitial } from '../../../utils/adminAccountDisplay';
import { adminErrorMessage } from '../../../utils/adminErrors';
import { validateProfessor, professorValidationError } from '../../../utils/adminProfessorValidation';
import { useState, useRef, useMemo, useEffect, useCallback } from 'react';
import { GraduationCap, Search, Plus, Eye, Pencil, Power, X } from 'lucide-react';
import PageShell from '../../../components/shared/PageShell';
import Button from '../../../components/shared/Button';
import { useToast } from '../../../context/notifications/useToast';
import { useAuth } from '../../../context/login/useAuth';
import { ADMIN_NAV_ITEMS, SUPERADMIN_NAV_ITEMS } from '../../../data/navigationData';
import {
  getProfessors,
  createProfessor,
  setProfessorStatus,
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

  const mutationVersion = useRef(0);
  const [statusUpdates, setStatusUpdates] = useState({});
  const fetchProfessors = useCallback(async (config) => {
    const version = mutationVersion.current;
    return { rows: await getProfessors(config), version };
  }, []);
  const { data: snapshot, isLoading, error: loadError, reload: loadProfessors } = useRemoteData(fetchProfessors, { rows: [], version: 0 });
  // A GET started before a committed status mutation cannot replace that status.
  // Preserve the GET's other fields (for example a freshly edited name/email).
  const professors = useMemo(() => snapshot.rows.map(professor => {
    const update = statusUpdates[professor.id];
    return update && update.version > snapshot.version ? { ...professor, status: update.status } : professor;
  }), [snapshot, statusUpdates]);
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

  const mounted = useRef(false);
  const modalIdentity = useRef(null);
  const pendingOperation = useRef(null);
  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; modalIdentity.current = null; pendingOperation.current = null; };
  }, []);

  function closeModal() {
    if (pendingOperation.current) return;
    modalIdentity.current = null;
    setShowCreateModal(false);
    setEditingProfessor(null);
    setViewingProfessor(null);
  }
  function beginModal() {
    if (!mounted.current || pendingOperation.current) return false;
    closeModal();
    modalIdentity.current = Symbol('professor-modal');
    return true;
  }
  function openViewModal(professor) {
    if (beginModal()) setViewingProfessor(professor);
  }
  function startOperation() {
    if (!mounted.current || pendingOperation.current || !modalIdentity.current) return null;
    const operation = { modal: modalIdentity.current };
    pendingOperation.current = operation;
    setIsSubmitting(true);
    return () => mounted.current && pendingOperation.current === operation && modalIdentity.current === operation.modal;
  }

  async function refetchProfessors(isCurrent) {
    const refreshed = await loadProfessors();
    if (!refreshed && isCurrent()) showToast("Saved, but the list could not refresh. Reload the page.", 'error');
  }

  const filteredProfessors = useMemo(() => {
    return professors.filter((p) =>
      accountText(p.name).toLowerCase().includes(searchQuery.toLowerCase()) ||
      accountText(p.email).toLowerCase().includes(searchQuery.toLowerCase())
    );
  }, [professors, searchQuery]);

  function openCreateModal() {
    if (!beginModal()) return;
    setName('');
    setEmail('');
    setPassword('');
    setProfessorId('');
    setFormError('');
    setShowCreateModal(true);
  }

  async function handleCreate(e) {
    e.preventDefault();
    if (pendingOperation.current || !modalIdentity.current) return;
    setFormError('');

    const validationError = validateProfessor({ name, email, password, professorId }, true);
    if (validationError) { setFormError(validationError); return; }

    const isCurrent = startOperation();
    if (!isCurrent) return;
    try {
      await createProfessor({ name: name.trim(), email: email.trim().toLowerCase(), password, professorId: professorId.trim() });
      if (!isCurrent()) return;
      showToast('Professor account created.', 'success');
      await refetchProfessors(isCurrent);
      if (isCurrent()) setShowCreateModal(false);
    } catch (error) {
      if (isCurrent()) setFormError(professorValidationError(error));
    } finally {
      if (isCurrent()) { pendingOperation.current = null; setIsSubmitting(false); }
    }
  }

  async function handleToggleStatus(professor) {
    if (toggleLock.current) return;
    toggleLock.current = true;
    setTogglingId(professor.id);
    try {
      const updated = await setProfessorStatus(professor.id, professor.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE');
      if (!mounted.current) return;
      const version = ++mutationVersion.current;
      setStatusUpdates(previous => ({ ...previous, [updated.id]: { status: updated.status, version } }));
      setViewingProfessor((previous) => previous?.id === updated.id ? updated : previous);
      showToast('Account status: ' + (STATUS_LABELS[updated.status] ?? updated.status), 'success');
    } catch (error) {
      if (mounted.current) showToast(adminErrorMessage(error), 'error');
    } finally {
      toggleLock.current = false;
      if (mounted.current) setTogglingId(null);
    }
  }

  function openEditModal(professor) {
    if (!beginModal()) return;
    setEditingProfessor(professor);
    setName(accountText(professor.name));
    setEmail(accountText(professor.email));
    setProfessorId(accountText(professor.professorId));
    setFormError('');
  }

  async function handleSaveEdit(e) {
    e.preventDefault();
    if (pendingOperation.current || !modalIdentity.current) return;
    setFormError('');

    const validationError = validateProfessor({ name, email, professorId });
    if (validationError) { setFormError(validationError); return; }

    const isCurrent = startOperation();
    if (!isCurrent) return;
    try {
      await updateProfessor(editingProfessor.id, { name: name.trim(), email: email.trim().toLowerCase(), professorId: professorId.trim() });
      if (!isCurrent()) return;
      showToast('Professor info updated.', 'success');
      await refetchProfessors(isCurrent);
      if (isCurrent()) setEditingProfessor(null);
    } catch (error) {
      if (isCurrent()) setFormError(professorValidationError(error));
    } finally {
      if (isCurrent()) { pendingOperation.current = null; setIsSubmitting(false); }
    }
  }

  return (
    <PageShell responsive
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
              aria-label="Search professors"
              placeholder="Search professors"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </div>
          <Button onClick={openCreateModal} disabled={isSubmitting}>
            <Plus size={16} style={{ marginRight: 6 }} />
            Create Professor
          </Button>
        </div>

        {isLoading ? (
          <p className={styles.loadingText}>Loading professors...</p>
        ) : loadError ? (
          <div className={styles.tableCard} style={{ padding: 'var(--space-12)', textAlign: 'center' }}>
            <p className={styles.loadingText}>Couldn't load professors. Please check your connection.</p>
            <button
              onClick={loadProfessors}
              style={{ marginTop: '12px', color: 'var(--color-accent-active)', textDecoration: 'underline', background: 'none', border: 'none', cursor: 'pointer' }}
            >
              Retry
            </button>
          </div>
        ) : (
          <div className={styles.tableCard} role="region" aria-label="Accounts table, scroll horizontally" tabIndex={0}>
            <div role="table" aria-label="Professors">
            <div role="row" className={styles.tableHeaderRow}>
              <span role="columnheader" id="professor-column-1">Name</span>
              <span role="columnheader" id="professor-column-2">Email</span>
              <span role="columnheader" id="professor-column-3">Professor ID</span>
              <span role="columnheader" id="professor-column-4">Account Action</span>
              <span role="columnheader" id="professor-column-5">Actions</span>
            </div>

            {filteredProfessors.map((prof) => (
              <div
                role="row"
                key={prof.id}
                className={`${styles.tableRow} ${styles.tableRowClickable}`}
                onClick={() => openViewModal(prof)}
              >
                <span role="cell" aria-describedby="professor-column-1" className={styles.profName}>{accountDisplay(prof.name)}</span>
                <span role="cell" aria-describedby="professor-column-2" className={styles.profEmail}>{accountDisplay(prof.email, 'Email not provided')}</span>
                <span role="cell" aria-describedby="professor-column-3" className={styles.profCount}>{prof.professorId}</span>
                <span role="cell" aria-describedby="professor-column-4" onClick={(e) => e.stopPropagation()}>
                  <button
                    className={`${styles.actionBtn} ${prof.status === 'ACTIVE' ? styles.actionBtnOff : ''}`}
                    onClick={() => handleToggleStatus(prof)}
                    disabled={togglingId !== null}
                    aria-label={prof.status === 'ACTIVE' ? 'Deactivate' : 'Reactivate'}
                  >
                    <Power size={15} aria-hidden="true" />
                    {prof.status === 'ACTIVE' ? 'Deactivate' : 'Reactivate'}
                  </button>
                </span>
                <span role="cell" aria-describedby="professor-column-5" className={styles.actions} onClick={(e) => e.stopPropagation()}>
                  <button
                    className={styles.actionBtn}
                    onClick={() => openViewModal(prof)}
                    disabled={isSubmitting}
                    aria-label="View"
                  >
                    <Eye size={15} aria-hidden="true" /> View
                  </button>
                  <button
                    className={styles.actionBtn}
                    onClick={() => openEditModal(prof)}
                    disabled={isSubmitting}
                    aria-label="Edit"
                  >
                    <Pencil size={15} aria-hidden="true" /> Edit
                  </button>

                </span>
              </div>
            ))}

            {filteredProfessors.length === 0 && (
              <p className={styles.emptyState}>No professors match your search.</p>
            )}
            </div>
          </div>
        )}
      </main>

      {showCreateModal && (
        <Dialog labelledBy="professor-create-title" onClose={closeModal} busy={isSubmitting} className={styles.modal}>
            <div className={styles.modalHeader}>
              <h3 id="professor-create-title" className={styles.modalTitle}>Create Professor Account</h3>
              <button disabled={isSubmitting} className={styles.closeBtn} onClick={closeModal} aria-label="Close">
                <X size={18} />
              </button>
            </div>
            <p className={styles.modalSubtitle}>
              Enter the professor's details and set an initial password.
            </p>

            <form onSubmit={handleCreate} className={styles.form}>
              <div className={styles.field}>
                <label htmlFor="professor-name" className={styles.label}>Fullname</label>
                <input
                  type="text"
                  className={styles.input}
                  id="professor-name"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                />
              </div>

              <div className={styles.field}>
                <label htmlFor="professor-email" className={styles.label}>Email</label>
                <input
                  type="email"
                  className={styles.input}
                  id="professor-email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                />
              </div>

              <div className={styles.field}>
                <label htmlFor="professor-professorId" className={styles.label}>Professor ID</label>
                <input
                  type="text"
                  className={styles.input}
                  id="professor-professorId"
                  value={professorId}
                  onChange={(e) => setProfessorId(e.target.value)}
                />
              </div>

              <div className={styles.field}>
                <label htmlFor="professor-password" className={styles.label}>Initial password</label>
                <input
                  type="password"
                  className={styles.input}
                  id="professor-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="At least 8 characters"
                />
              </div>

              {formError && <p className={styles.formError}>{formError}</p>}

              <Button type="submit" isLoading={isSubmitting}>Create Professor</Button>
            </form>
        </Dialog>
      )}

      {viewingProfessor && (
        <Dialog labelledBy="professor-view-title" onClose={closeModal} busy={isSubmitting} className={styles.modal}>
            <div className={styles.modalHeader}>
              <div className={styles.profileHeaderRow}>
                <div className={styles.profileAvatar}>
                  {accountInitial(viewingProfessor.name, true)}
                </div>
                <div>
                  <h3 id="professor-view-title" className={styles.modalTitle}>{accountDisplay(viewingProfessor.name)}</h3>
                  <p className={styles.profileEmail}>{accountDisplay(viewingProfessor.email, 'Email not provided')}</p>
                </div>
              </div>
              <button disabled={isSubmitting} className={styles.closeBtn} onClick={closeModal} aria-label="Close">
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
        </Dialog>
      )}

      {editingProfessor && (
        <Dialog labelledBy="professor-edit-title" onClose={closeModal} busy={isSubmitting} className={styles.modal}>
            <div className={styles.modalHeader}>
              <div className={styles.profileHeaderRow}>
                <div className={styles.profileAvatar}>
                  {accountInitial(editingProfessor.name, true)}
                </div>
                <div>
                  <h3 id="professor-edit-title" className={styles.modalTitle}>Edit Professor Account</h3>
                  <p className={styles.profileEmail}>{accountDisplay(editingProfessor.email, 'Email not provided')}</p>
                </div>
              </div>
              <button disabled={isSubmitting} className={styles.closeBtn} onClick={closeModal} aria-label="Close">
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleSaveEdit} className={styles.form}>
              <div className={styles.field}>
                <label htmlFor="professor-name" className={styles.label}>Fullname</label>
                <input
                  type="text"
                  className={styles.input}
                  id="professor-name"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                />
              </div>

              <div className={styles.field}>
                <label htmlFor="professor-email" className={styles.label}>Email</label>
                <input
                  type="email"
                  className={styles.input}
                  id="professor-email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                />
              </div>

              <div className={styles.field}>
                <label htmlFor="professor-professorId" className={styles.label}>Professor ID</label>
                <input
                  type="text"
                  className={styles.input}
                  id="professor-professorId"
                  value={professorId}
                  onChange={(e) => setProfessorId(e.target.value)}
                />
              </div>

              {formError && <p className={styles.formError}>{formError}</p>}

              <Button type="submit" isLoading={isSubmitting}>Save Changes</Button>
            </form>
        </Dialog>
      )}
    </PageShell>
  );
}