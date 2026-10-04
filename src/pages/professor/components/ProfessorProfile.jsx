import { logProfessorError } from '../../../utils/professorDiagnostics';
import { useState, useEffect } from 'react';
import { User as UserIcon, KeyRound } from 'lucide-react';
import Sidebar from '../../../components/shared/Sidebar';
import ChangePasswordModal from '../../../components/shared/ChangePasswordModal';
import { PROFESSOR_NAV_ITEMS } from '../../../data/navigationData';
import { getProfessorProfile } from '../../../services/professor/professorService';
import { STATUS_LABELS } from '../../../utils/backendContract';
import styles from '../styles/ProfessorProfile.module.css';

// ✅ Wired to the real backend — ProfessorProfileResponse has
// { userId, name, email, role, status, professorId }. No avatarInitial,
// title, or dateJoined fields exist — those were mock-only.
//
// The "Edit Professor ID" feature was removed — only GET /professor/profile
// exists on the backend so far, no update route yet (updateProfessorProfile()
// in professorService.js is still flagged as unavailable).

export default function ProfessorProfile() {
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [profile, setProfile] = useState(null);
  const [showChangePassword, setShowChangePassword] = useState(false);

  useEffect(() => {
    let isMounted = true;
    async function loadData() {
      try {
        const data = await getProfessorProfile();
        if (isMounted) setProfile(data);
      } catch (err) {
        logProfessorError('load-profile', err);
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
        <Sidebar navItems={PROFESSOR_NAV_ITEMS} sectionLabel="Prof" roleBadge="Professor" />
        <div className={styles.contentArea}>
          <p className={styles.loadingText}>Loading profile...</p>
        </div>
      </div>
    );
  }

  if (loadError || !profile) {
    return (
      <div className={styles.layout}>
        <Sidebar navItems={PROFESSOR_NAV_ITEMS} sectionLabel="Prof" roleBadge="Professor" />
        <div className={styles.contentArea}>
          <p className={styles.loadingText}>Couldn't load your profile. Please refresh and try again.</p>
        </div>
      </div>
    );
  }

  const avatarInitial = profile.name?.charAt(0).toUpperCase() ?? '?';

  return (
    <div className={styles.layout}>
      <Sidebar navItems={PROFESSOR_NAV_ITEMS} sectionLabel="Prof" roleBadge="Professor" />

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
              <p className={styles.subMeta}>Professor</p>
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
            <span className={styles.infoLabel}>Professor ID</span>
            <span className={styles.infoValue}>{profile.professorId}</span>
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
      </div>

      <ChangePasswordModal isOpen={showChangePassword} onClose={() => setShowChangePassword(false)} />
    </div>
  );
}