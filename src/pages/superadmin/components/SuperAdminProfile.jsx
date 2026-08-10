import { useState, useEffect } from 'react';
import { User as UserIcon, KeyRound } from 'lucide-react';
import Sidebar from '../../../components/shared/Sidebar';
import { useToast } from '../../../context/notifications/ToastContext';
import { SUPERADMIN_NAV_ITEMS } from '../../../data/navigationData';
import { getSuperAdminProfile } from '../../../services/superadmin/superadminService';
import styles from '../styles/SuperAdminProfile.module.css';

export default function SuperAdminProfile() {
  const { showToast } = useToast();
  const [isLoading, setIsLoading] = useState(true);
  const [profile, setProfile] = useState(null);

  useEffect(() => {
    let isMounted = true;
    async function loadData() {
      const data = await getSuperAdminProfile();
      if (isMounted) {
        setProfile(data);
        setIsLoading(false);
      }
    }
    loadData();
    return () => { isMounted = false; };
  }, []);

  function handleComingSoon(feature) {
    showToast(`${feature} coming soon.`, 'info');
  }

  if (isLoading || !profile) {
    return (
      <div className={styles.layout}>
        <Sidebar navItems={SUPERADMIN_NAV_ITEMS} />
        <div className={styles.contentArea}>
          <p className={styles.loadingText}>Loading profile...</p>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.layout}>
      <Sidebar navItems={SUPERADMIN_NAV_ITEMS} />

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
            onClick={() => handleComingSoon('Changing your password')}
          >
            <span className={styles.actionIcon}>
              <KeyRound size={16} />
            </span>
            Change password
          </button>
        </main>
      </div>
    </div>
  );
}