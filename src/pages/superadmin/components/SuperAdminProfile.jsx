import { useState, useEffect } from 'react';
import { User as UserIcon, KeyRound } from 'lucide-react';
import Sidebar from '../../../components/shared/Sidebar';
import ChangePasswordModal from '../../../components/shared/ChangePasswordModal';
import { useToast } from '../../../context/notifications/ToastContext';
import { SUPERADMIN_NAV_ITEMS } from '../../../data/navigationData';
import { getSuperAdminProfile } from '../../../services/superadmin/superadminService';
import styles from '../styles/SuperAdminProfile.module.css';

export default function SuperAdminProfile() {
  const { showToast } = useToast();
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [profile, setProfile] = useState(null);
  const [showChangePassword, setShowChangePassword] = useState(false);

  useEffect(() => {
    let isMounted = true;
    async function loadData() {
      try {
        const data = await getSuperAdminProfile();
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

  function handleComingSoon(feature) {
    showToast(`${feature} coming soon.`, 'info');
  }

  if (isLoading) {
    return (
      <div className={styles.layout}>
        <Sidebar navItems={SUPERADMIN_NAV_ITEMS} sectionLabel="Superadmin" roleBadge="Superadmin" />
        <div className={styles.contentArea}>
          <p className={styles.loadingText}>Loading profile...</p>
        </div>
      </div>
    );
  }

  if (loadError || !profile) {
    return (
      <div className={styles.layout}>
        <Sidebar navItems={SUPERADMIN_NAV_ITEMS} sectionLabel="Superadmin" roleBadge="Superadmin" />
        <div className={styles.contentArea}>
          <p className={styles.loadingText}>Couldn't load your profile. Please refresh and try again.</p>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.layout}>
      <Sidebar navItems={SUPERADMIN_NAV_ITEMS} sectionLabel="Superadmin" roleBadge="Superadmin" />

      <div className={styles.contentArea}>
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
            <div className={styles.infoValueRow}>
              <span className={styles.infoValue}>{profile.fullName}</span>
              <button className={styles.editLink} onClick={() => handleComingSoon('Editing your name')}>
                Edit
              </button>
            </div>
          </div>

          <div className={styles.infoCard}>
            <span className={styles.infoLabel}>Email</span>
            <span className={styles.infoValue}>{profile.email}</span>
          </div>

          <div className={styles.infoCard}>
            <span className={styles.infoLabel}>Date joined</span>
            <span className={styles.infoValue}>{profile.dateJoined}</span>
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
      </div>

      <ChangePasswordModal isOpen={showChangePassword} onClose={() => setShowChangePassword(false)} />
    </div>
  );
}