import { useAuth } from '../../../context/login/useAuth';
import { apiErrorMessage } from '../../../utils/apiErrors';
import { useState, useEffect } from 'react';
import { User as UserIcon, KeyRound } from 'lucide-react';
import PageShell from '../../../components/shared/PageShell';
import ChangePasswordModal from '../../../components/shared/ChangePasswordModal';
import { SUPERADMIN_NAV_ITEMS } from '../../../data/navigationData';
import { getSuperAdminProfile } from '../../../services/superadmin/superadminService';
import { STATUS_LABELS } from '../../../utils/backendContract';
import styles from '../styles/SuperAdminProfile.module.css';

// ✅ Wired to the real backend — UserProfileResponse only has
// { userId, name, email, role, status }. No avatarInitial, title, or
// dateJoined fields exist — those were mock-only. Avatar initial is
// derived client-side from the name.

export default function SuperAdminProfile() {
  const { user } = useAuth();
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [profile, setProfile] = useState(null);
  const [showChangePassword, setShowChangePassword] = useState(user?.mustChangePassword === true);

  useEffect(() => {
    let isMounted = true;
    async function loadData() {
      try {
        const data = await getSuperAdminProfile();
        if (isMounted) setProfile(data);
      } catch (err) {
  
        if (isMounted) setLoadError(apiErrorMessage(err));
      } finally {
        if (isMounted) setIsLoading(false);
      }
    }
    loadData();
    return () => { isMounted = false; };
  }, []);

  if (isLoading) {
    return (
      <PageShell responsive navItems={SUPERADMIN_NAV_ITEMS} sectionLabel="Superadmin" roleBadge="Superadmin">
        <div>
          <p className={styles.loadingText}>Loading profile...</p>
        </div>
      </PageShell>
    );
  }

  if (loadError || !profile) {
    return (
      <PageShell responsive navItems={SUPERADMIN_NAV_ITEMS} sectionLabel="Superadmin" roleBadge="Superadmin">
        <div>
          <p className={styles.loadingText}>{loadError || 'Could not load this page.'}</p>
        <button onClick={() => window.location.reload()}>Retry</button>
        </div>
      </PageShell>
    );
  }

  const avatarInitial = profile.name?.charAt(0).toUpperCase() ?? '?';

  return (
    <PageShell responsive navItems={SUPERADMIN_NAV_ITEMS} sectionLabel="Superadmin" roleBadge="Superadmin">
        <header className={styles.topbar}>
          <div className={styles.breadcrumb}>
            <UserIcon size={16} />
            Profile
          </div>
        </header>

        <main className={styles.main}>
          {user?.mustChangePassword && <p role="alert">Change your temporary password before using Super Admin features.</p>}
          <div className={styles.profileHeader}>
            <div className={styles.avatar}>{avatarInitial}</div>
            <div>
              <p className={styles.fullName}>{profile.name}</p>
              <p className={styles.subMeta}>Super Admin</p>
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
            <span className={styles.infoValue}>{STATUS_LABELS[profile.status] ?? profile.status}</span>
          </div>

          <h2 className={styles.sectionTitle}>Account</h2>

          <button
            className={styles.actionRow}
            onClick={event => { event.currentTarget.focus(); setShowChangePassword(true); }}
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