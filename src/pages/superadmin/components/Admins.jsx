import { useState, useEffect } from 'react';
import { Users, Plus, Trash2, X } from 'lucide-react';
import Sidebar from '../../../components/shared/Sidebar';
import Button from '../../../components/shared/Button';
import { useToast } from '../../../context/notifications/ToastContext';
import { SUPERADMIN_NAV_ITEMS } from '../../../data/navigationData';
import { getAdmins, addAdmin, deleteAdmin } from '../../../services/superadmin/superadminService';
import styles from '../styles/Admins.module.css';

const INSTITUTIONAL_DOMAIN = '@paterostechnologicalcollege.edu.ph';

export default function Admins() {
  const { showToast } = useToast();

  const [isLoading, setIsLoading] = useState(true);
  const [admins, setAdmins] = useState([]);
  const [showAddModal, setShowAddModal] = useState(false);

  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [formError, setFormError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    loadAdmins();
  }, []);

  async function loadAdmins() {
    setIsLoading(true);
    const data = await getAdmins();
    setAdmins(data);
    setIsLoading(false);
  }

  function openAddModal() {
    setFullName('');
    setEmail('');
    setFormError('');
    setShowAddModal(true);
  }

  async function handleAddAdmin(e) {
    e.preventDefault();
    setFormError('');

    if (!fullName || !email) {
      setFormError('Please fill in all fields.');
      return;
    }

    if (!email.toLowerCase().endsWith(INSTITUTIONAL_DOMAIN)) {
      setFormError(`Email must end with ${INSTITUTIONAL_DOMAIN}`);
      return;
    }

    setIsSubmitting(true);
    try {
      await addAdmin({ fullName, email });
      showToast('Admin account created.', 'success');
      setShowAddModal(false);
      loadAdmins();
    } catch (err) {
      setFormError('Something went wrong. Please try again.');
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleDelete(admin) {
    if (!window.confirm(`Remove ${admin.fullName} as an Admin?`)) return;
    await deleteAdmin(admin.id);
    showToast('Admin removed.', 'success');
    loadAdmins();
  }

  return (
    <div className={styles.layout}>
      <Sidebar navItems={SUPERADMIN_NAV_ITEMS} />

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
          ) : (
            <div className={styles.tableCard}>
              <div className={styles.tableHeaderRow}>
                <span>Name</span>
                <span>Email</span>
                <span>Date added</span>
                <span>Status</span>
                <span></span>
              </div>

              {admins.map((admin) => (
                <div key={admin.id} className={styles.tableRow}>
                  <span className={styles.adminName}>{admin.fullName}</span>
                  <span className={styles.adminEmail}>{admin.email}</span>
                  <span className={styles.adminDate}>{admin.dateAdded}</span>
                  <span className={`${styles.statusBadge} ${styles[`status_${admin.status}`]}`}>
                    {admin.status === 'active' ? 'Active' : 'Inactive'}
                  </span>
                  <button
                    className={styles.deleteBtn}
                    onClick={() => handleDelete(admin)}
                    aria-label={`Remove ${admin.fullName}`}
                  >
                    <Trash2 size={16} />
                  </button>
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
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
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

              {formError && <p className={styles.formError}>{formError}</p>}

              <Button type="submit" isLoading={isSubmitting}>Create Admin account</Button>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}