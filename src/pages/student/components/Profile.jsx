import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { User as UserIcon, Pencil, KeyRound } from 'lucide-react';
import Sidebar from '../../../components/shared/Sidebar';
import Button from '../../../components/shared/Button';
import ChangePasswordModal from '../../../components/shared/ChangePasswordModal';
import { useToast } from '../../../context/notifications/ToastContext';
import { STUDENT_NAV_ITEMS } from '../../../data/navigationData';
import { getProfileData, getCurrentTopicAndWeek, getOverallModuleProgress } from '../../../services/student/studentService';
import styles from '../styles/Profile.module.css';

export default function Profile() {
  const { showToast } = useToast();
  const navigate = useNavigate();

  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [profile, setProfile] = useState(null);
  const [currentLesson, setCurrentLesson] = useState(null);
  const [moduleProgress, setModuleProgress] = useState(null);
  const [showChangePassword, setShowChangePassword] = useState(false);

  useEffect(() => {
    let isMounted = true;

    async function loadData() {
      try {
        const [profileData, lesson, moduleProg] = await Promise.all([
          getProfileData(),
          getCurrentTopicAndWeek(),
          getOverallModuleProgress(),
        ]);
        if (isMounted) {
          setProfile(profileData);
          setCurrentLesson(lesson);
          setModuleProgress(moduleProg);
        }
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

  function goToQuiz() {
    const { topic, week } = currentLesson || {};
    if (topic && week) navigate(`/student/quiz/${topic.id}/${week.id}`);
  }

  const hasCurrentLesson = Boolean(currentLesson?.topic && currentLesson?.week);

  // TODO: replace with real navigation to an edit form/modal, or a PATCH
  // call to your backend, once those flows exist.
  function handleComingSoon(feature) {
    showToast(`${feature} coming soon.`, 'info');
  }

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

  return (
    <div className={styles.layout}>
      <Sidebar navItems={STUDENT_NAV_ITEMS} progress={moduleProgress} />

      <div className={styles.contentArea}>
        <header className={styles.topbar}>
          <div className={styles.breadcrumb}>
            <UserIcon size={16} />
            Profile
          </div>
          <Button onClick={goToQuiz} disabled={!hasCurrentLesson}>Start quiz</Button>
        </header>

        <main className={styles.main}>
          <div className={styles.profileHeader}>
            <div className={styles.avatar}>{profile.avatarInitial}</div>
            <div>
              <p className={styles.fullName}>{profile.fullName}</p>
              <p className={styles.subMeta}>{profile.program} · {profile.yearLevel}</p>
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
            <span className={styles.infoLabel}>Student ID</span>
            <span className={styles.infoValue}>{profile.studentId}</span>
          </div>

          <div className={styles.infoCard}>
            <span className={styles.infoLabel}>Year Level</span>
            <span className={styles.infoValue}>{profile.yearLevel}</span>
          </div>

          <div className={styles.infoCard}>
            <span className={styles.infoLabel}>Program</span>
            <span className={styles.infoValue}>{profile.program}</span>
          </div>

          <h2 className={styles.sectionTitle}>Account</h2>

          <button
            className={styles.actionRow}
            onClick={() => handleComingSoon('Editing basic information')}
          >
            <span className={styles.actionIcon}>
              <Pencil size={16} />
            </span>
            Edit Basic Information
          </button>

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