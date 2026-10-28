import Topbar from '../../../components/shared/Topbar';
import { apiErrorMessage } from '../../../utils/apiErrors';
import { useState, useRef, useEffect } from 'react';
import { Users, Plus, Trash2, X, Power } from 'lucide-react';
import PageShell from '../../../components/shared/PageShell';
import Dialog from '../../../components/shared/Dialog';
import Button from '../../../components/shared/Button';
import { useToast } from '../../../context/notifications/useToast';
import { SUPERADMIN_NAV_ITEMS } from '../../../data/navigationData';
import { getAdmins, addAdmin, setAdminStatus, deleteAdmin } from '../../../services/superadmin/superadminService';
import { useRemoteData } from '../../../hooks/useRemoteData';
import { STATUS_LABELS } from '../../../utils/backendContract';
import styles from '../styles/Admins.module.css';

const INSTITUTIONAL_DOMAIN = '@paterostechnologicalcollege.edu.ph';

// âœ… getAdmins() returns AdminListResponse â€” { id, name, email, role,
// status } â€” confirmed to include both id and status, so toggle works.
// (Only the create-admin response is limited to name/email/role â€” not
// used for display here.)
//
export default function Admins() {
  const { showToast } = useToast();

  const { data: admins, isLoading, error: loadError, reload: loadAdmins } = useRemoteData(getAdmins, []);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [deleteError, setDeleteError] = useState('');
  const [protectedHistory, setProtectedHistory] = useState(false);
  const protectedDelete = useRef(false);
  const [deleting, setDeleting] = useState(false);
  const deleteLock = useRef(false);
  const statusLock = useRef(false);
  const [statusTarget, setStatusTarget] = useState(null);
  const [statusError, setStatusError] = useState('');
  const createLock = useRef(false);
  const instance = useRef(0);
  const mounted = useRef(false);
  useEffect(() => {
    mounted.current = true;
    instance.current += 1;
    return () => {
      mounted.current = false;
      instance.current += 1;
    };
  }, []);
  const [showAddModal, setShowAddModal] = useState(false);
  const [updatingStatusId, setUpdatingStatusId] = useState(null);

  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [formError, setFormError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  function resetForm() {
    setName('');
    setEmail('');
    setPassword('');
    setFormError('');
  }

  function closeAddModal() {
    if (createLock.current) return;
    instance.current += 1;
    resetForm();
    setShowAddModal(false);
  }

  function openAddModal(event) {
    if (deleteTarget || statusTarget || createLock.current || statusLock.current) return;
    event?.currentTarget?.focus();
    instance.current += 1;
    setName('');
    setEmail('');
    setPassword('');
    setFormError('');
    setShowAddModal(true);
  }

  async function handleAddAdmin(e) {
    e.preventDefault();
    if (createLock.current || statusLock.current || !showAddModal) return;
    setFormError('');

    if (!name.trim() || !email.trim() || !password.trim()) {
      setFormError('Please fill in all fields.');
      return;
    }
    if (name.trim().length > 100 || email.trim().length > 254) {
      setFormError('Name must be at most 100 characters; email at most 254.');
      return;
    }
    if (password.length < 12 || new TextEncoder().encode(password).length > 72) {
      setFormError('Password must be at least 12 characters and at most 72 UTF-8 bytes.');
      return;
    }

    createLock.current = true;
    const requestInstance = instance.current;
    const isCurrent = () => mounted.current && instance.current === requestInstance;
    setIsSubmitting(true);
    try {
      await addAdmin({ name, email, password });
      if (!isCurrent()) return;
      resetForm();
      setShowAddModal(false);
      showToast('Admin account created.', 'success');
      await loadAdmins();
    } catch (err) {

      if (isCurrent()) setFormError(apiErrorMessage(err));
    } finally {
      if (isCurrent()) {
        createLock.current = false;
        setIsSubmitting(false);
      }
    }
  }

  function openStatus(admin, event) {
    if (deleteTarget || statusLock.current || createLock.current) return;
    event?.currentTarget?.focus();
    setStatusError(''); setStatusTarget(admin);
  }
  function closeStatus() {
    if (!statusLock.current) { setStatusTarget(null); setStatusError(''); }
  }
  async function handleSetStatus() {
    if (!statusTarget) return;
    const admin = statusTarget;
    if (deleteTarget || statusLock.current || createLock.current) return;
    statusLock.current = true;
    const isCurrent = () => mounted.current;
    setUpdatingStatusId(admin.id);
    setStatusError('');
    try {
      const updated = await setAdminStatus(admin.id, admin.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE');
      if (!isCurrent()) return;
      setStatusTarget(null);
      showToast('Account status: ' + (STATUS_LABELS[updated.status] ?? updated.status), 'success');
      await loadAdmins();
    } catch (err) {
      if (isCurrent()) setStatusError(apiErrorMessage(err, "Couldn't update the account status. Please try again."));
    } finally {
      if (isCurrent()) {
        statusLock.current = false;
        setUpdatingStatusId(null);
      }
    }
  }

  function closeDelete() { if (!deleteLock.current) { setDeleteTarget(null); setDeleteError(''); } }
  async function confirmDelete() {
    if (!deleteTarget || deleteLock.current || protectedDelete.current) return;
    deleteLock.current = true; setDeleting(true); setDeleteError('');
    try {
      await deleteAdmin(deleteTarget.id);
      if (!mounted.current) return;
      setDeleteTarget(null); showToast('Admin account deleted.', 'success');
      await loadAdmins();
    } catch (error) {
      if (mounted.current) {
        if (error?.response?.status === 409 && error.response.data?.code === 'ACCOUNT_HAS_PROTECTED_REFERENCES') {
          protectedDelete.current = true;
          setProtectedHistory(true);
        }
        setDeleteError(apiErrorMessage(error, 'Could not delete this Admin account. Please try again.'));
      }
    } finally { deleteLock.current = false; if (mounted.current) setDeleting(false); }
  }

  function deactivateProtectedAdmin() {
    if (!deleteTarget || !protectedDelete.current || deleteLock.current || deleteTarget.status === 'INACTIVE') return;
    setStatusError(''); setStatusTarget(deleteTarget); setDeleteTarget(null);
  }

  return (
    <PageShell responsive navItems={SUPERADMIN_NAV_ITEMS} sectionLabel="Superadmin" roleBadge="Superadmin">
        <Topbar>
          <div className={styles.breadcrumb}>
            <Users size={16} />
            Admins
          </div>
          <Button onClick={openAddModal} disabled={isSubmitting || updatingStatusId !== null}>
            <Plus size={16} style={{ marginRight: 6 }} />
            Add Admin
          </Button>
        </Topbar>

        <main className={styles.main}>
          {isLoading ? (
            <div className={`${styles.tableCard} ${styles.stateCard}`} role="status"><p className={styles.loadingText}>Loading admins...</p></div>
          ) : loadError ? (
            <div className={`${styles.tableCard} ${styles.stateCard}`} role="alert">
              <p className={styles.loadingText}>{apiErrorMessage(loadError, "Couldn't load admins. Please check your connection.")}</p>
              <button
                onClick={loadAdmins}
                className={styles.retryBtn}
              >
                Retry
              </button>
            </div>
          ) : (
            <div className={styles.tableScroll} role="region" aria-label="Admin accounts" tabIndex={0}>
            <div className={styles.tableCard} role="table" aria-label="Administrator accounts">
              <div className={styles.tableHeaderRow} role="row">
                <span role="columnheader">Name</span>
                <span role="columnheader">Email</span>
                <span role="columnheader">Status</span>
                <span role="columnheader">Actions</span>
              </div>

              {admins.map((admin) => (
                <div key={admin.id} className={styles.tableRow} role="row">
                  <span className={styles.adminName} role="rowheader" title={admin.name}>{admin.name}</span>
                  <span className={styles.adminEmail} role="cell" title={admin.email}>{admin.email}</span>
                  <span className={styles.statusCell} role="cell">
                    <span className={`${styles.statusBadge} ${styles[`status_${admin.status?.toLowerCase()}`] || styles.status_unknown}`}>
                      {STATUS_LABELS[admin.status] ?? admin.status ?? 'Not set'}
                    </span>
                  </span>
                  <div className={styles.actionsCell} role="cell">
                    <button
                      className={`${styles.actionBtn} ${admin.status === 'ACTIVE' ? styles.deactivateAction : styles.activateAction}`}
                      onClick={event => openStatus(admin, event)}
                      disabled={updatingStatusId !== null || isSubmitting}
                      aria-label={admin.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
                      title={`${admin.status === 'ACTIVE' ? 'Deactivate' : 'Activate'} ${admin.name}`}
                    >
                      <Power size={15} />
                    </button>
                    <button
                      className={`${styles.actionBtn} ${styles.deleteBtn}`}
                      onClick={event => { event.currentTarget.focus(); setDeleteError(''); protectedDelete.current = false; setProtectedHistory(false); setDeleteTarget(admin); }}
                      disabled={isSubmitting || updatingStatusId !== null || deleting}
                      aria-label={`Delete ${admin.name}`}
                      title={`Delete ${admin.name}`}
                    >
                      <Trash2 size={16} />
                    </button>
                  </div>
                </div>
              ))}

              {admins.length === 0 && (
                <div role="row"><div role="cell" aria-colspan={4} className={styles.emptyState}>No admins yet. Click "Add Admin" to create one.</div></div>
              )}
            </div>
            </div>
          )}
        </main>

      {statusTarget && <Dialog labelledBy="admin-status-title" describedBy="admin-status-description" onClose={closeStatus}
        busy={updatingStatusId !== null} initialFocus="button" className={styles.modal}>
        <div className={styles.form}>
          <h3 id="admin-status-title" className={styles.modalTitle}>{statusTarget.status === 'ACTIVE' ? 'Deactivate' : 'Activate'} Admin Account?</h3>
          <p id="admin-status-description" className={styles.adminName}>
            {statusTarget.status === 'ACTIVE'
              ? <>Deactivate <strong>{statusTarget.name}</strong>? They will lose access to HALO and their existing sessions will be revoked. Account information and history will remain preserved.</>
              : <>Activate <strong>{statusTarget.name}</strong>? They will regain access to HALO after signing in again.</>}
          </p>
          {statusError && <p role="alert" className={styles.formError}>{statusError}</p>}
          {updatingStatusId !== null && <p role="status">Updating account status...</p>}
          <div className={styles.confirmActions}>
            <Button variant="secondary" onClick={closeStatus} disabled={updatingStatusId !== null}>Cancel</Button>
            <Button variant={statusTarget.status === 'ACTIVE' ? 'danger' : 'primary'} onClick={handleSetStatus}
              disabled={updatingStatusId !== null}>{statusTarget.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}</Button>
          </div>
        </div>
      </Dialog>}
      {deleteTarget && <Dialog labelledBy="delete-admin-title" onClose={closeDelete} busy={deleting} className={styles.modal}>
        <h3 id="delete-admin-title" className={styles.modalTitle}>Delete Admin Account?</h3>
        <p>This will permanently remove this Admin account. This action cannot be undone.</p>
        <p>{deleteTarget.name}</p>
        {protectedHistory && <p role="alert" className={styles.formError}>This Admin has protected history and cannot be deleted. Deactivate the account instead.</p>}
        {deleteError && (!protectedHistory || deleteError !== 'This Admin has protected history and cannot be deleted. Deactivate the account instead.') && <p role="alert" className={styles.formError}>{deleteError}</p>}
        <Button variant="secondary" onClick={closeDelete} disabled={deleting}>Cancel</Button>
        {protectedHistory
          ? deleteTarget.status !== 'INACTIVE' && <Button key="deactivate" variant="secondary" onClick={deactivateProtectedAdmin} isLoading={deleting}>Deactivate Account</Button>
          : <Button key="delete" variant="danger" onClick={confirmDelete} isLoading={deleting}>Delete Account</Button>}
      </Dialog>}
      {showAddModal && (
        <Dialog labelledBy="add-admin-title" describedBy={formError ? 'add-admin-error' : undefined} onClose={closeAddModal} busy={isSubmitting} className={styles.modal}>
            <div className={styles.modalHeader}>
              <h3 id="add-admin-title" className={styles.modalTitle}>Add new Admin</h3>
              <button className={styles.closeBtn} onClick={closeAddModal} disabled={isSubmitting} aria-label="Close">
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleAddAdmin} className={styles.form}>
              <div className={styles.field}>
                <label htmlFor="admin-name" className={styles.label}>Full name</label>
                <input
                  id="admin-name"
                  type="text"
                  className={styles.input}
                  disabled={isSubmitting}
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="Juan Dela Cruz"
                />
              </div>

              <div className={styles.field}>
                <label htmlFor="admin-email" className={styles.label}>Email</label>
                <input
                  id="admin-email"
                  disabled={isSubmitting}
                  type="email"
                  className={styles.input}
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder={`name${INSTITUTIONAL_DOMAIN}`}
                />
              </div>

              <div className={styles.field}>
                <label htmlFor="admin-password" className={styles.label}>Initial password</label>
                <input
                  id="admin-password"
                  disabled={isSubmitting}
                  type="password"
                  className={styles.input}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="At least 12 characters"
                />
              </div>

              {formError && <p id="add-admin-error" role="alert" className={styles.formError}>{formError}</p>}

              <Button type="button" variant="secondary" onClick={closeAddModal} disabled={isSubmitting}>Cancel</Button>
              <Button type="submit" isLoading={isSubmitting}>Create Admin account</Button>
            </form>
        </Dialog>
      )}
    </PageShell>
  );
}
