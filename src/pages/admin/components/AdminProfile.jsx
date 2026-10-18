import ProfileName from '../../../components/shared/ProfileName';
import ProfilePhoto from '../../../components/shared/ProfilePhoto';
import { useState, useEffect } from 'react';
import { useAuth } from '../../../context/login/useAuth';
import { User as UserIcon, KeyRound } from 'lucide-react';
import PageShell from '../../../components/shared/PageShell';
import ChangePasswordModal from '../../../components/shared/ChangePasswordModal';
import { ADMIN_NAV_ITEMS } from '../../../data/navigationData';
import { getAdminProfile } from '../../../services/admin/adminService';
import { STATUS_LABELS } from '../../../utils/backendContract';
import styles from '../styles/AdminProfile.module.css';

// ✅ Wired to the real backend — UserProfileResponse only has
// { userId, name, email, role, status }. No adminId, avatarInitial,
// title, or dateJoined fields exist for Admins on this backend — those
// were mock-only. Avatar initial is derived client-side from the name.

const noProfilePhoto = async () => null;

export default function AdminProfile() {
  const { user } = useAuth();
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [profile, setProfile] = useState(null);
  const [showChangePassword, setShowChangePassword] = useState(user?.mustChangePassword === true);

  useEffect(() => {
    let isMounted = true;
    async function loadData() {
      try {
        const data = await getAdminProfile();
        if (isMounted) setProfile(data);
      } catch {
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
      <PageShell responsive navItems={ADMIN_NAV_ITEMS} sectionLabel="Admin" roleBadge="Admin">
        <p className={styles.loadingText}>Loading profile...</p>
      </PageShell>
    );
  }

  if (loadError || !profile) {
    return (
      <PageShell responsive navItems={ADMIN_NAV_ITEMS} sectionLabel="Admin" roleBadge="Admin">
        <p className={styles.loadingText}>Couldn't load your profile. Please refresh and try again.</p>
      </PageShell>
    );
  }

  const displayName = (user?.name ?? profile.name);
  const avatarInitial = displayName?.trim().charAt(0).toUpperCase() || '?';

  return (
    <PageShell responsive navItems={ADMIN_NAV_ITEMS} sectionLabel="Admin" roleBadge="Admin">
      <header className={styles.topbar}>
        <div className={styles.breadcrumb}>
          <UserIcon size={16} />
          Profile
        </div>
      </header>

      <main className={styles.main}>
        {user?.mustChangePassword && <p role="alert">Change your temporary password before using Admin features.</p>}
        <div className={styles.profileHeader}>
          <ProfilePhoto identity={profile.userId} initial={avatarInitial} readOnly={Boolean(user?.mustChangePassword)} getProfilePhoto={user?.mustChangePassword ? noProfilePhoto : undefined} identityContent={<div><p className={styles.fullName}>{displayName}</p><p className={styles.subMeta}>Admin</p></div>} />
        </div>

        <div className={styles.infoCard}>
          <ProfileName key={profile.userId} name={displayName} readOnly={Boolean(user?.mustChangePassword)} />
        </div>

        <div className={styles.infoCard}>
          <span className={styles.infoLabel}>Email</span>
          <span className={styles.infoValue}>{profile.email}</span>
        </div>

        <div className={styles.infoCard}>
          <span className={styles.infoLabel}>Status</span>
          <span className={styles.infoValue}>{STATUS_LABELS[profile.status] ?? profile.status}</span>
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

      <ChangePasswordModal accessible isOpen={showChangePassword} onClose={() => setShowChangePassword(false)} />
    </PageShell>
  );
}
