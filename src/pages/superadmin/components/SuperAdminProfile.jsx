import Topbar from '../../../components/shared/Topbar';
import ProfileName from '../../../components/shared/ProfileName';
import ProfilePhoto from '../../../components/shared/ProfilePhoto';
import { useAuth } from '../../../context/login/useAuth';
import { apiErrorMessage } from '../../../utils/apiErrors';
import { useState, useEffect } from 'react';
import { User as UserIcon } from 'lucide-react';
import PageShell from '../../../components/shared/PageShell';
import { SUPERADMIN_NAV_ITEMS } from '../../../data/navigationData';
import { getSuperAdminProfile } from '../../../services/superadmin/superadminService';
import { STATUS_LABELS } from '../../../utils/backendContract';
import styles from '../styles/SuperAdminProfile.module.css';

// ✅ Wired to the real backend — UserProfileResponse only has
// { userId, name, email, role, status }. No avatarInitial, title, or
// dateJoined fields exist — those were mock-only. Avatar initial is
// derived client-side from the name.

const noProfilePhoto = async () => null;

export default function SuperAdminProfile() {
  const { user } = useAuth();
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [profile, setProfile] = useState(null);

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
      <Topbar title="Profile" />
        <div>
          <p className={styles.loadingText}>Loading profile...</p>
        </div>
      </PageShell>
    );
  }

  if (loadError || !profile) {
    return (
      <PageShell responsive navItems={SUPERADMIN_NAV_ITEMS} sectionLabel="Superadmin" roleBadge="Superadmin">
      <Topbar title="Profile" />
        <div>
          <p className={styles.loadingText}>{loadError || 'Could not load this page.'}</p>
        <button onClick={() => window.location.reload()}>Retry</button>
        </div>
      </PageShell>
    );
  }

  const displayName = (user?.name ?? profile.name);
  const avatarInitial = displayName?.trim().charAt(0).toUpperCase() || '?';

  return (
    <PageShell responsive navItems={SUPERADMIN_NAV_ITEMS} sectionLabel="Superadmin" roleBadge="Superadmin">
        <Topbar>
          <div className={styles.breadcrumb}>
            <UserIcon size={16} />
            Profile
          </div>
        </Topbar>

        <main className={styles.main}>
          <div className={styles.profileHeader}>
            <ProfilePhoto identity={profile.userId} initial={avatarInitial} readOnly={Boolean(user?.mustChangePassword)} getProfilePhoto={user?.mustChangePassword ? noProfilePhoto : undefined} identityContent={<div><p className={styles.fullName}>{displayName}</p><p className={styles.subMeta}>Super Admin</p></div>} />
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


        </main>

    </PageShell>
  );
}