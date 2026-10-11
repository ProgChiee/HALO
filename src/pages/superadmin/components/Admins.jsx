import { apiErrorMessage } from '../../../utils/apiErrors';
import { useState, useRef, useEffect } from 'react';
import { Users, Plus, Trash2, X, Power } from 'lucide-react';
import PageShell from '../../../components/shared/PageShell';
import Dialog from '../../../components/shared/Dialog';
import Button from '../../../components/shared/Button';
import { useToast } from '../../../context/notifications/useToast';
import { SUPERADMIN_NAV_ITEMS } from '../../../data/navigationData';
import { getAdmins, addAdmin, setAdminStatus } from '../../../services/superadmin/superadminService';
import { useRemoteData } from '../../../hooks/useRemoteData';
import { STATUS_LABELS } from '../../../utils/backendContract';
import styles from '../styles/Admins.module.css';

const INSTITUTIONAL_DOMAIN = '@paterostechnologicalcollege.edu.ph';

// ✅ getAdmins() returns AdminListResponse — { id, name, email, role,
// status } — confirmed to include both id and status, so toggle works.
// (Only the create-admin response is limited to name/email/role — not
// used for display here.)
//
// ⚠️ Still no DELETE endpoint for admins — only status toggling
// (ACTIVE/INACTIVE/BLOCKED). "Remove" stays disabled below; ask your
// backend team for DELETE /api/super-admin/admin/{id} if a true removal
// action is needed.

export default function Admins() {
  const { showToast } = useToast();

  const { data: admins, isLoading, error: loadError, reload: loadAdmins } = useRemoteData(getAdmins, []);
  const statusLock = useRef(false);
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
    if (createLock.current || statusLock.current) return;
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

  async function handleSetStatus(admin) {
    if (statusLock.current || createLock.current) return;
    statusLock.current = true;
    const isCurrent = () => mounted.current;
    setUpdatingStatusId(admin.id);
    try {
      const updated = await setAdminStatus(admin.id, admin.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE');
      if (!isCurrent()) return;
      showToast('Account status: ' + (STATUS_LABELS[updated.status] ?? updated.status), 'success');
      await loadAdmins();
    } catch (err) {
      if (isCurrent()) showToast(apiErrorMessage(err, "Couldn't update the account status. Please try again."), 'error');
    } finally {
      if (isCurrent()) {
        statusLock.current = false;
        setUpdatingStatusId(null);
      }
    }
  }

  function handleDelete() {
    // ⚠️ Disabled: no DELETE endpoint for admins yet — see note above.
    showToast('Removing admins is not available yet — deactivate instead.', 'error');
  }

  return (
    <PageShell responsive navItems={SUPERADMIN_NAV_ITEMS} sectionLabel="Superadmin" roleBadge="Superadmin">
        <header className={styles.topbar}>
          <div className={styles.breadcrumb}>
            <Users size={16} />
            Admins
          </div>
          <Button onClick={openAddModal} disabled={isSubmitting || updatingStatusId !== null}>
            <Plus size={16} style={{ marginRight: 6 }} />
            Add Admin
          </Button>
        </header>

        <main className={styles.main}>
          {isLoading ? (
            <p className={styles.loadingText}>Loading admins...</p>
          ) : loadError ? (
            <div className={styles.tableCard} style={{ padding: 'var(--space-12)', textAlign: 'center' }}>
              <p className={styles.loadingText}>{apiErrorMessage(loadError, "Couldn't load admins. Please check your connection.")}</p>
              <button
                onClick={loadAdmins}
                style={{ marginTop: '12px', color: 'var(--color-accent-active)', textDecoration: 'underline', background: 'none', border: 'none', cursor: 'pointer' }}
              >
                Retry
              </button>
            </div>
          ) : (
            <div className={styles.tableScroll} role="region" aria-label="Admin accounts" tabIndex={0}>
            <div className={styles.tableCard}>
              <div className={styles.tableHeaderRow}>
                <span>Name</span>
                <span>Email</span>
                <span>Status</span>
                <span>Actions</span>
              </div>

              {admins.map((admin) => (
                <div key={admin.id} className={styles.tableRow}>
                  <span className={styles.adminName}>{admin.name}</span>
                  <span className={styles.adminEmail}>{admin.email}</span>
                  <span className={`${styles.statusText} ${styles[`status_${admin.status?.toLowerCase()}`]}`}>
                    {admin.status === 'ACTIVE' ? 'Active' : admin.status === 'BLOCKED' ? 'Blocked' : 'Inactive'}
                  </span>
                  <div className={styles.actionsCell}>
                    <button
                      className={`${styles.actionBtn} ${admin.status !== 'ACTIVE' ? styles.actionBtnOff : ''}`}
                      onClick={() => handleSetStatus(admin)}
                      disabled={updatingStatusId !== null || isSubmitting}
                      aria-label={admin.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
                    >
                      <Power size={15} />
                    </button>
                    <button
                      className={styles.deleteBtn}
                      onClick={() => handleDelete(admin)}
                      disabled
                      title="Not available yet — no delete endpoint on the backend"
                      aria-label={`Remove ${admin.name} (not available yet)`}
                    >
                      <Trash2 size={16} />
                    </button>
                  </div>
                </div>
              ))}

              {admins.length === 0 && (
                <p className={styles.emptyState}>No admins yet. Click "Add Admin" to create one.</p>
              )}
            </div>
            </div>
          )}
        </main>

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