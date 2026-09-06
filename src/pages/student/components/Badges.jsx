import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Award, Lock } from 'lucide-react';
import Sidebar from '../../../components/shared/Sidebar';
import Button from '../../../components/shared/Button';
import { STUDENT_NAV_ITEMS } from '../../../data/navigationData';
import { getBadgesData, getCurrentTopicAndWeek, getOverallModuleProgress } from '../../../services/student/studentService';
import styles from '../styles/Badges.module.css';

export default function Badges() {
  const navigate = useNavigate();

  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [badgesData, setBadgesData] = useState(null);
  const [currentLesson, setCurrentLesson] = useState(null);
  const [moduleProgress, setModuleProgress] = useState(null);

  useEffect(() => {
    let isMounted = true;

    async function loadData() {
      try {
        const [data, lesson, moduleProg] = await Promise.all([
          getBadgesData(),
          getCurrentTopicAndWeek(),
          getOverallModuleProgress(),
        ]);
        if (isMounted) {
          setBadgesData(data);
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

  if (isLoading) {
    return (
      <div className={styles.layout}>
        <Sidebar navItems={STUDENT_NAV_ITEMS} progress={null} />
        <div className={styles.contentArea}>
          <p className={styles.loadingText}>Loading your badges...</p>
        </div>
      </div>
    );
  }

  if (loadError || !badgesData) {
    return (
      <div className={styles.layout}>
        <Sidebar navItems={STUDENT_NAV_ITEMS} progress={null} />
        <div className={styles.contentArea}>
          <p className={styles.loadingText}>Couldn't load your badges. Please refresh and try again.</p>
        </div>
      </div>
    );
  }

  const { earned, locked } = badgesData;

  return (
    <div className={styles.layout}>
      <Sidebar navItems={STUDENT_NAV_ITEMS} progress={moduleProgress} />

      <div className={styles.contentArea}>
        <header className={styles.topbar}>
          <div className={styles.breadcrumb}>
            <Award size={16} />
            Badges
            <span className={styles.countPill}>{earned.length} earned</span>
          </div>
          <Button onClick={goToQuiz} disabled={!hasCurrentLesson}>Start quiz</Button>
        </header>

        <main className={styles.main}>
          <h2 className={styles.sectionTitle}>Earned</h2>
          <div className={styles.badgesGrid}>
            {earned.map((badge) => (
              <div key={badge.id} className={styles.badgeCard}>
                <span className={styles.badgeEmoji}>{badge.emoji}</span>
                <p className={styles.badgeTitle}>{badge.title}</p>
                <p className={styles.badgeDescription}>{badge.description}</p>
                <span className={styles.earnedPill}>Earned</span>
              </div>
            ))}
          </div>

          <h2 className={styles.sectionTitle}>Locked</h2>
          <div className={styles.badgesGrid}>
            {locked.map((badge) => (
              <div key={badge.id} className={styles.badgeCardLocked}>
                <span className={styles.lockIcon}>
                  <Lock size={22} />
                </span>
                <p className={styles.badgeTitleLocked}>{badge.title}</p>
                <p className={styles.badgeDescriptionLocked}>{badge.description}</p>
              </div>
            ))}
          </div>
        </main>
      </div>
    </div>
  );
}