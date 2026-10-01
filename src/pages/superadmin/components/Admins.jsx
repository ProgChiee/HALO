import { useState, useRef } from 'react';
import { Users, Plus, Trash2, X, Power } from 'lucide-react';
import Sidebar from '../../../components/shared/Sidebar';
import Button from '../../../components/shared/Button';
import { useToast } from '../../../context/notifications/useToast';
import { SUPERADMIN_NAV_ITEMS } from '../../../data/navigationData';
import { getAdmins, addAdmin, toggleAdminStatus } from '../../../services/superadmin/superadminService';
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

  const { data: admins, setData: setAdmins, isLoading, error: loadError, reload: loadAdmins } = useRemoteData(getAdmins, []);
  const toggleLock = useRef(false);
  const [showAddModal, setShowAddModal] = useState(false);
  const [togglingId, setTogglingId] = useState(null);

  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [formError, setFormError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function refetchAdmins() {
    try {
    const data = await getAdmins();
    setAdmins(data);
    } catch {
      showToast("Saved, but the list could not refresh. Reload the page.", 'error');
    }
  }

  function openAddModal() {
    setName('');
    setEmail('');
    setPassword('');
    setFormError('');
    setShowAddModal(true);
  }

  async function handleAddAdmin(e) {
    e.preventDefault();
    if (isSubmitting) return;
    setFormError('');

    if (!name || !email || !password) {
      setFormError('Please fill in all fields.');
      return;
    }
    if (!email.toLowerCase().endsWith(INSTITUTIONAL_DOMAIN)) {
      setFormError(`Email must end with ${INSTITUTIONAL_DOMAIN}`);
      return;
    }
    if (password.length < 8) {
      setFormError('Password must be at least 8 characters.');
      return;
    }

    setIsSubmitting(true);
    try {
      await addAdmin({ name, email, password });
      showToast('Admin account created.', 'success');
      setShowAddModal(false);
      await refetchAdmins();
    } catch (err) {
      console.error(err);
      setFormError('Something went wrong. Please try again.');
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleToggleStatus(admin) {
    if (toggleLock.current) return;
    toggleLock.current = true;
    setTogglingId(admin.id);
    try {
      const updated = await toggleAdminStatus(admin.id);
      setAdmins((previous) => previous.map((item) => item.id === updated.id ? updated : item));
      showToast('Account status: ' + (STATUS_LABELS[updated.status] ?? updated.status), 'success');
    } catch {
      showToast("Couldn't update the account status. Please try again.", 'error');
    } finally {
      toggleLock.current = false;
      setTogglingId(null);
    }
  }

  function handleDelete() {
    // ⚠️ Disabled: no DELETE endpoint for admins yet — see note above.
    showToast('Removing admins is not available yet — deactivate instead.', 'error');
  }

  return (
    <div className={styles.layout}>
      <Sidebar navItems={SUPERADMIN_NAV_ITEMS} sectionLabel="Superadmin" roleBadge="Superadmin" />

      <div className={styles.contentArea}>
        <header className={styles.topbar}>
          <div className={styles.breadcrumb}>
            <Users size={16} />
            Admins
          </div>
          <Button onClick={openAddModal}>
            <Plus size={16} style={{ marginRight: 6 }} />
            Add Admin
          </Button>
        </header>

        <main className={styles.main}>
          {isLoading ? (
            <p className={styles.loadingText}>Loading admins...</p>
          ) : loadError ? (
            <div className={styles.tableCard} style={{ padding: '3rem', textAlign: 'center' }}>
              <p className={styles.loadingText}>Couldn't load admins. Please check your connection.</p>
              <button
                onClick={loadAdmins}
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
                      onClick={() => handleToggleStatus(admin)}
                      disabled={togglingId !== null}
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
          )}
        </main>
      </div>

      {showAddModal && (
        <div className={styles.modalOverlay} onClick={() => setShowAddModal(false)}>
          <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
            <div className={styles.modalHeader}>
              <h3 className={styles.modalTitle}>Add new Admin</h3>
              <button className={styles.closeBtn} onClick={() => setShowAddModal(false)} aria-label="Close">
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleAddAdmin} className={styles.form}>
              <div className={styles.field}>
                <label className={styles.label}>Full name</label>
                <input
                  type="text"
                  className={styles.input}
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="Juan Dela Cruz"
                />
              </div>

              <div className={styles.field}>
                <label className={styles.label}>Email</label>
                <input
                  type="email"
                  className={styles.input}
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder={`name${INSTITUTIONAL_DOMAIN}`}
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

              <Button type="submit" isLoading={isSubmitting}>Create Admin account</Button>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}