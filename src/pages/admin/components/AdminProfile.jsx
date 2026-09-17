import { useState, useEffect } from 'react';
import { User as UserIcon, KeyRound } from 'lucide-react';
import PageShell from '../../../components/shared/PageShell';
import ChangePasswordModal from '../../../components/shared/ChangePasswordModal';
import { ADMIN_NAV_ITEMS } from '../../../data/navigationData';
import { getAdminProfile } from '../../../services/admin/adminService';
import styles from '../styles/AdminProfile.module.css';

// ✅ Wired to the real backend — UserProfileResponse only has
// { userId, name, email, role, status }. No adminId, avatarInitial,
// title, or dateJoined fields exist for Admins on this backend — those
// were mock-only. Avatar initial is derived client-side from the name.
// There's also no update endpoint yet, so "Edit" actions are removed
// until one exists.

export default function AdminProfile() {
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [profile, setProfile] = useState(null);
  const [showChangePassword, setShowChangePassword] = useState(false);

  useEffect(() => {
    let isMounted = true;
    async function loadData() {
      try {
        const data = await getAdminProfile();
        if (isMounted) setProfile(data);
      } catch (err) {
        console.error(err);
        if (isMounted) setLoadError(true);
      } finally {
        if (isMounted) setIsLoading(false);
      }
    }
    loadData();
    return () => { isMounted = false; };
  }, []);

  if (isLoading) {
    return (
      <PageShell navItems={ADMIN_NAV_ITEMS} sectionLabel="Admin" roleBadge="Admin">
        <p className={styles.loadingText}>Loading profile...</p>
      </PageShell>
    );
  }

  if (loadError || !profile) {
    return (
      <PageShell navItems={ADMIN_NAV_ITEMS} sectionLabel="Admin" roleBadge="Admin">
        <p className={styles.loadingText}>Couldn't load your profile. Please refresh and try again.</p>
      </PageShell>
    );
  }

  const avatarInitial = profile.name?.charAt(0).toUpperCase() ?? '?';

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
          <div className={styles.avatar}>{avatarInitial}</div>
          <div>
            <p className={styles.fullName}>{profile.name}</p>
            <p className={styles.subMeta}>Admin</p>
          </div>
        </div>

        <div className={styles.infoCard}>
          <span className={styles.infoLabel}>Full name</span>
          <span className={styles.infoValue}>{profile.name}</span>
        </div>

        <div className={styles.infoCard}>
          <span className={styles.infoLabel}>Email</span>
          <span className={styles.infoValue}>{profile.email}</span>
        </div>

        <div className={styles.infoCard}>
          <span className={styles.infoLabel}>Status</span>
          <span className={styles.infoValue}>{profile.status === 'ACTIVE' ? 'Active' : 'Inactive'}</span>
        </div>

        <h2 className={styles.sectionTitle}>Account</h2>

        <button
          className={styles.actionRow}
          onClick={() => setShowChangePassword(true)}
        >
          <span className={styles.actionIcon}>
            <KeyRound size={16} />
          </span>
          Change password
        </button>
      </main>

      <ChangePasswordModal isOpen={showChangePassword} onClose={() => setShowChangePassword(false)} />
    </PageShell>
  );
}