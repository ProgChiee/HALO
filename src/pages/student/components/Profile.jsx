import Topbar from '../../../components/shared/Topbar';
import { useAuth } from '../../../context/login/useAuth';
import ProfileName from '../../../components/shared/ProfileName';
import ProfilePhoto from './ProfilePhoto';
import { useStudentWorkflow } from '../../../context/student/useStudentWorkflow';
import { logStudentError } from '../../../utils/studentDiagnostics';
import { useState, useEffect } from 'react';
import { User as UserIcon } from 'lucide-react';
import StudentPageShell from './StudentPageShell';
import styles from '../styles/Profile.module.css';
import AcademicProfileNotice from './AcademicProfileNotice';
import AcademicProfileField from './AcademicProfileField';
import { yearLabels } from '../../../utils/registration';

export default function Profile() {
  const { user } = useAuth();
  const workflow = useStudentWorkflow();
  const { getProfileData } = workflow.api;
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [profile, setProfile] = useState(null);
  const [reload, setReload] = useState(0);

  useEffect(() => {
    let isMounted = true;
    async function loadData() {
      setIsLoading(true);
      setLoadError(false);
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
  }, [getProfileData, reload]);

  if (isLoading) {
    return (
      <StudentPageShell>
      <Topbar title="Profile" />
        <div className={styles.contentArea}>
          <p className={styles.loadingText}>Loading your profile...</p>
        </div>
      </StudentPageShell>
    );
  }

  if (loadError || !profile) {
    return (
      <StudentPageShell>
      <Topbar title="Profile" />
        <div className={styles.contentArea}>
          <p className={styles.loadingText}>Couldn't load your profile. Please refresh and try again.</p>
        </div>
      </StudentPageShell>
    );
  }

  const displayName = workflow.acting ? profile.name : (user?.name ?? profile.name);
  const avatarInitial = displayName?.trim().charAt(0).toUpperCase() || '?';
  const yearLevelLabel = yearLabels[profile.yearLevel] ?? profile.yearLevel ?? 'Not set';

  return (
    <StudentPageShell>

      <div className={styles.contentArea}>
        <Topbar>
          <div className={styles.breadcrumb}>
            <UserIcon size={16} />
            Profile
          </div>
        </Topbar>

        <main className={styles.main}>
          {profile.profileIncomplete && <AcademicProfileNotice onRefresh={() => setReload(value => value + 1)} />}
          <div className={styles.profileHeader}>
            <ProfilePhoto key={workflow.identity} initial={avatarInitial} readOnly={workflow.acting} identityContent={<div>
              <p className={styles.fullName}>{displayName}</p>
              <p className={styles.subMeta}>{profile.section?.trim() || 'Section not set'} · {yearLevelLabel}</p>
            </div>} />
          </div>

          <div className={styles.detailsGrid}>
          <div className={styles.infoCard}>
            <ProfileName key={workflow.identity ?? workflow.basePath} name={displayName} readOnly={workflow.acting} />
          </div>

          <div className={styles.infoCard}>
            <span className={styles.infoLabel}>Email</span>
            <span className={styles.infoValue}>{profile.email}</span>
          </div>

          {[['studentId', 'Student ID'], ['section', 'Section'], ['yearLevel', 'Year Level']].map(([field, label]) => <div key={field} className={styles.infoCard}>
            <AcademicProfileField key={`${workflow.identity ?? workflow.basePath}:${profile.userId}:${field}`} field={field} label={label}
              value={profile[field]} readOnly={Boolean(workflow.acting || profile.profileIncomplete)}
              onSaved={value => setProfile(current => ({ ...current, [field]: value }))} />
          </div>)}

          </div>


        </main>
      </div>

    </StudentPageShell>
  );
}
