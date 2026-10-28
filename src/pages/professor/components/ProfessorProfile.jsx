import ProfessorIdField from './ProfessorIdField';
import Topbar from '../../../components/shared/Topbar';
import { useAuth } from '../../../context/login/useAuth';
import ProfileName from '../../../components/shared/ProfileName';
import ProfilePhoto from '../../../components/shared/ProfilePhoto';
import { professorErrorMessage } from '../../../utils/professorErrors';
import { logProfessorError } from '../../../utils/professorDiagnostics';
import { useState, useEffect } from 'react';
import { User as UserIcon } from 'lucide-react';
import PageShell from './ProfessorPageShell';
import { PROFESSOR_NAV_ITEMS } from '../../../data/navigationData';
import { useProfessorWorkflow } from '../../../context/professor/useProfessorWorkflow';
import { STATUS_LABELS } from '../../../utils/backendContract';
import styles from '../styles/ProfessorProfile.module.css';

export default function ProfessorProfile() {
  const { user } = useAuth();
  const workflow = useProfessorWorkflow();
  const { getProfessorProfile } = workflow.api;
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState(null);
  const [retry, setRetry] = useState(0);
  const [profile, setProfile] = useState(null);

  useEffect(() => {
    let isMounted = true;
    async function loadData() {
      try {
        const data = await getProfessorProfile();
        if (isMounted) setProfile(data);
      } catch (err) {
        logProfessorError('load-profile', err);
        if (isMounted) setLoadError(err);
      } finally {
        if (isMounted) setIsLoading(false);
      }
    }
    loadData();
    return () => { isMounted = false; };
  }, [retry, getProfessorProfile]);

  if (isLoading) {
    return (
      <PageShell responsive navItems={PROFESSOR_NAV_ITEMS} sectionLabel="Prof" roleBadge="Professor">
      <Topbar title="Profile" />
        <div className={styles.contentArea}>
          <p className={styles.loadingText}>Loading profile...</p>
        </div>
      </PageShell>
    );
  }

  if (loadError || !profile) {
    return (
      <PageShell responsive navItems={PROFESSOR_NAV_ITEMS} sectionLabel="Prof" roleBadge="Professor">
      <Topbar title="Profile" />
        <div className={styles.contentArea}>
          <p className={styles.loadingText}>{professorErrorMessage(loadError, "Couldn't load your profile. Please try again.")}</p>
          <button onClick={() => { setIsLoading(true); setLoadError(null); setRetry(value => value + 1); }}>Retry</button>
        </div>
      </PageShell>
    );
  }

  const displayName = workflow.acting ? profile.name : (user?.name ?? profile.name);
  const avatarInitial = displayName?.trim().charAt(0).toUpperCase() || '?';

  return (
    <PageShell responsive navItems={PROFESSOR_NAV_ITEMS} sectionLabel="Prof" roleBadge="Professor">

      <div className={styles.contentArea}>
        <Topbar>
          <div className={styles.breadcrumb}>
            <UserIcon size={16} />
            Profile
          </div>
        </Topbar>

        <main className={styles.main}>
          <div className={styles.profileHeader}>
            <ProfilePhoto identity={`${workflow.basePath}:${profile.userId}`} initial={avatarInitial} readOnly={workflow.acting} getProfilePhoto={workflow.acting ? workflow.api.getProfilePhoto : undefined} identityContent={<div><p className={styles.fullName}>{displayName}</p><p className={styles.subMeta}>Professor</p></div>} />
          </div>

          <div className={styles.infoCard}>
            <ProfileName key={workflow.identity ?? workflow.basePath} name={displayName} readOnly={workflow.acting} />
          </div>

          <div className={styles.infoCard}>
            <span className={styles.infoLabel}>Email</span>
            <span className={styles.infoValue}>{profile.email}</span>
          </div>

          <div className={styles.infoCard}>
            <ProfessorIdField key={workflow.identity ?? workflow.basePath} value={profile.professorId} readOnly={workflow.acting} onSaved={professorId => setProfile(current => ({ ...current, professorId }))} />
          </div>

          <div className={styles.infoCard}>
            <span className={styles.infoLabel}>Status</span>
            <span className={styles.infoValue}>{STATUS_LABELS[profile.status] ?? profile.status}</span>
          </div>


        </main>
      </div>

    </PageShell>
  );
}