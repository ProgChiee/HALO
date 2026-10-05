import { logStudentError } from '../../../utils/studentDiagnostics';
import { useState, useEffect } from 'react';
import { User as UserIcon, KeyRound } from 'lucide-react';
import Sidebar from '../../../components/shared/Sidebar';
import ChangePasswordModal from '../../../components/shared/ChangePasswordModal';
import { STUDENT_NAV_ITEMS } from '../../../data/navigationData';
import { getProfileData } from '../../../services/student/studentService';
import styles from '../styles/Profile.module.css';

// ✅ Wired to the real backend — StudentProfileResponse has
// { userId, name, email, role, status, studentId, section, yearLevel }.
// No "program" field exists — was mock-only, removed. No update endpoint
// exists yet either, so "Edit" actions stay out until the backend adds one.

const YEAR_LEVEL_LABELS = {
  FIRST_YEAR: '1st Year',
  SECOND_YEAR: '2nd Year',
};

export default function Profile() {
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [profile, setProfile] = useState(null);
  const [showChangePassword, setShowChangePassword] = useState(false);

  useEffect(() => {
    let isMounted = true;
    async function loadData() {
      try {
        const data = await getProfileData();
        if (isMounted) setProfile(data);
      } catch (err) {
        logStudentError('load-profile', err);
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
      <div className={styles.layout}>
        <Sidebar navItems={STUDENT_NAV_ITEMS} progress={null} />
        <div className={styles.contentArea}>
          <p className={styles.loadingText}>Loading your profile...</p>
        </div>
      </div>
    );
  }

  if (loadError || !profile) {
    return (
      <div className={styles.layout}>
        <Sidebar navItems={STUDENT_NAV_ITEMS} progress={null} />
        <div className={styles.contentArea}>
          <p className={styles.loadingText}>Couldn't load your profile. Please refresh and try again.</p>
        </div>
      </div>
    );
  }

  const avatarInitial = profile.name?.charAt(0).toUpperCase() ?? '?';
  const yearLevelLabel = YEAR_LEVEL_LABELS[profile.yearLevel] ?? profile.yearLevel;

  return (
    <div className={styles.layout}>
      <Sidebar navItems={STUDENT_NAV_ITEMS} progress={null} />

      <div className={styles.contentArea}>
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
              <p className={styles.subMeta}>{profile.section} · {yearLevelLabel}</p>
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
            <span className={styles.infoLabel}>Student ID</span>
            <span className={styles.infoValue}>{profile.studentId}</span>
          </div>

          <div className={styles.infoCard}>
            <span className={styles.infoLabel}>Section</span>
            <span className={styles.infoValue}>{profile.section}</span>
          </div>

          <div className={styles.infoCard}>
            <span className={styles.infoLabel}>Year Level</span>
            <span className={styles.infoValue}>{yearLevelLabel}</span>
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