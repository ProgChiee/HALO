import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Award, Lock } from 'lucide-react';
import Sidebar from '../../../components/shared/Sidebar';
import Button from '../../../components/shared/Button';
import { STUDENT_NAV_ITEMS } from '../../../data/navigationData';
import { mockModuleProgress } from '../../../data/student/studentDashboardData';
import { getBadgesData, getCurrentTopicAndWeek } from '../../../services/student/studentService';
import styles from '../styles/Badges.module.css';

export default function Badges() {
  const navigate = useNavigate();

  const [isLoading, setIsLoading] = useState(true);
  const [badgesData, setBadgesData] = useState(null);
  const [currentLesson, setCurrentLesson] = useState(null);

  useEffect(() => {
    let isMounted = true;

    async function loadData() {
      const [data, lesson] = await Promise.all([
        getBadgesData(),
        getCurrentTopicAndWeek(),
      ]);
      if (isMounted) {
        setBadgesData(data);
        setCurrentLesson(lesson);
        setIsLoading(false);
      }
    }

    loadData();
    return () => { isMounted = false; };
  }, []);

  function goToQuiz() {
    const { topic, week } = currentLesson || {};
    if (topic && week) navigate(`/student/quiz/${topic.id}/${week.id}`);
  }

  if (isLoading || !badgesData) {
    return (
      <div className={styles.layout}>
        <Sidebar navItems={STUDENT_NAV_ITEMS} progress={null} />
        <div className={styles.contentArea}>
          <p className={styles.loadingText}>Loading your badges...</p>
        </div>
      </div>
    );
  }

  const { earned, locked } = badgesData;

  return (
    <div className={styles.layout}>
      <Sidebar navItems={STUDENT_NAV_ITEMS} progress={mockModuleProgress} />

      <div className={styles.contentArea}>
        <header className={styles.topbar}>
          <div className={styles.breadcrumb}>
            <Award size={16} />
            Badges
            <span className={styles.countPill}>{earned.length} earned</span>
          </div>
          <Button onClick={goToQuiz}>Start quiz</Button>
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