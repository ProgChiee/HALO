import { useState, useEffect } from 'react';
import { User as UserIcon, KeyRound, X } from 'lucide-react';
import PageShell from '../../../components/shared/PageShell';
import Button from '../../../components/shared/Button';
import { useToast } from '../../../context/notifications/ToastContext';
import { ADMIN_NAV_ITEMS } from '../../../data/navigationData';
import { getAdminProfile, updateAdminProfile } from '../../../services/admin/adminService';
import styles from '../styles/AdminProfile.module.css';

export default function AdminProfile() {
  const { showToast } = useToast();
  const [isLoading, setIsLoading] = useState(true);
  const [profile, setProfile] = useState(null);

  const [showEditModal, setShowEditModal] = useState(false);
  const [adminId, setAdminId] = useState('');
  const [formError, setFormError] = useState('');
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    let isMounted = true;
    async function loadData() {
      const data = await getAdminProfile();
      if (isMounted) {
        setProfile(data);
        setIsLoading(false);
      }
    }
    loadData();
    return () => { isMounted = false; };
  }, []);

  function openEditModal() {
    setAdminId(profile.adminId);
    setFormError('');
    setShowEditModal(true);
  }

  async function handleSaveEdit(e) {
    e.preventDefault();
    setFormError('');

    if (!adminId.trim()) {
      setFormError('Please enter an Admin ID.');
      return;
    }

    setIsSaving(true);
    try {
      const updated = await updateAdminProfile({ adminId: adminId.trim() });
      setProfile(updated);
      showToast('Admin ID updated.', 'success');
      setShowEditModal(false);
    } catch (err) {
      setFormError('Something went wrong. Please try again.');
    } finally {
      setIsSaving(false);
    }
  }

  function handleComingSoon(feature) {
    showToast(`${feature} coming soon.`, 'info');
  }

  if (isLoading || !profile) {
    return (
      <PageShell navItems={ADMIN_NAV_ITEMS} sectionLabel="Admin" roleBadge="Admin">
        <p className={styles.loadingText}>Loading profile...</p>
      </PageShell>
    );
  }

  return (
    <PageShell navItems={ADMIN_NAV_ITEMS} sectionLabel="Admin" roleBadge="Admin">
      <header className={styles.topbar}>
        <div className={styles.breadcrumb}>
          <UserIcon size={16} />
          Profile
        </div>
      </header>

      <main className={styles.main}>
        <div className={styles.profileHeader}>
          <div className={styles.avatar}>{profile.avatarInitial}</div>
          <div>
            <p className={styles.fullName}>{profile.fullName}</p>
            <p className={styles.subMeta}>{profile.title}</p>
          </div>
        </div>

        <div className={styles.infoCard}>
          <span className={styles.infoLabel}>Full name</span>
          <span className={styles.infoValue}>{profile.fullName}</span>
        </div>

        <div className={styles.infoCard}>
          <span className={styles.infoLabel}>Email</span>
          <span className={styles.infoValue}>{profile.email}</span>
        </div>

        <div className={styles.infoCard}>
          <span className={styles.infoLabel}>Admin ID</span>
          <div className={styles.infoValueRow}>
            <span className={styles.infoValue}>{profile.adminId}</span>
            <button className={styles.editLink} onClick={openEditModal}>
              Edit
            </button>
          </div>
        </div>

        <div className={styles.infoCard}>
          <span className={styles.infoLabel}>Date joined</span>
          <span className={styles.infoValue}>{profile.dateJoined}</span>
        </div>

        <h2 className={styles.sectionTitle}>Account</h2>

        <button
          className={styles.actionRow}
          onClick={() => handleComingSoon('Changing your password')}
        >
          <span className={styles.actionIcon}>
            <KeyRound size={16} />
          </span>
          Change password
        </button>
      </main>

      {showEditModal && (
        <div className={styles.modalOverlay} onClick={() => setShowEditModal(false)}>
          <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
            <div className={styles.modalHeader}>
              <h3 className={styles.modalTitle}>Edit Admin ID</h3>
              <button className={styles.closeBtn} onClick={() => setShowEditModal(false)} aria-label="Close">
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleSaveEdit} className={styles.form}>
              <div className={styles.field}>
                <label className={styles.label}>Admin ID</label>
                <input
                  type="text"
                  className={styles.input}
                  value={adminId}
                  onChange={(e) => setAdminId(e.target.value)}
                  placeholder="e.g. ADMIN-2026-002"
                />
              </div>

              {formError && <p className={styles.formError}>{formError}</p>}

              <Button type="submit" isLoading={isSaving}>Save Changes</Button>
            </form>
          </div>
        </div>
      )}
    </PageShell>
  );
}