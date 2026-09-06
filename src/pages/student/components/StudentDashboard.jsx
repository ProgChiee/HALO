import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { LayoutGrid, BookOpen, TrendingUp, Award, CheckCircle2 } from 'lucide-react';
import Sidebar from '../../../components/shared/Sidebar';
import Button from '../../../components/shared/Button';
import { useAuth } from '../../../context/login/AuthContext';
import { STUDENT_NAV_ITEMS } from '../../../data/navigationData';
import { getDashboardData, getCurrentTopicAndWeek } from '../../../services/student/studentService';
import styles from '../styles/StudentDashboard.module.css';

// Maps the string icon names from the mock data to actual icon components
const ICONS = { BookOpen, TrendingUp, Award, CheckCircle2 };

export default function StudentDashboard() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const firstName = user?.name?.split(' ')[0] ?? 'there';

  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [dashboardData, setDashboardData] = useState(null);
  const [currentLesson, setCurrentLesson] = useState(null);

  useEffect(() => {
    let isMounted = true;

    async function loadData() {
      try {
        const [data, lesson] = await Promise.all([
          getDashboardData(),
          getCurrentTopicAndWeek(),
        ]);
        if (isMounted) {
          setDashboardData(data);
          setCurrentLesson(lesson);
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

  function goToLesson() {
    const { topic, week } = currentLesson || {};
    if (topic && week) navigate(`/student/lesson/${topic.id}/${week.id}`);
  }

  function goToQuiz() {
    const { topic, week } = currentLesson || {};
    if (topic && week) navigate(`/student/quiz/${topic.id}/${week.id}`);
  }

  if (isLoading) {
    return (
      <div className={styles.layout}>
        <Sidebar navItems={STUDENT_NAV_ITEMS} progress={null} />
        <div className={styles.contentArea}>
          <p className={styles.loadingText}>Loading your dashboard...</p>
        </div>
      </div>
    );
  }

  if (loadError || !dashboardData) {
    return (
      <div className={styles.layout}>
        <Sidebar navItems={STUDENT_NAV_ITEMS} progress={null} />
        <div className={styles.contentArea}>
          <p className={styles.loadingText}>Couldn't load your dashboard. Please refresh and try again.</p>
        </div>
      </div>
    );
  }

  const { stats, enrolledSubjects, moduleProgress } = dashboardData;
  const hasCurrentLesson = Boolean(currentLesson?.topic && currentLesson?.week);

  return (
    <div className={styles.layout}>
      <Sidebar navItems={STUDENT_NAV_ITEMS} progress={moduleProgress} />

      <div className={styles.contentArea}>
        <header className={styles.topbar}>
          <div className={styles.breadcrumb}>
            <LayoutGrid size={16} />
            Dashboard
          </div>
          <Button onClick={goToQuiz} disabled={!hasCurrentLesson}>Start quiz</Button>
        </header>

        <main className={styles.main}>
          <h1 className={styles.greeting}>Welcome back, {firstName}! 👋</h1>
          <p className={styles.subtext}>Here's your learning overview for today</p>

          <div className={styles.statsGrid}>
            {stats.map((stat) => {
              const Icon = ICONS[stat.icon];
              return (
                <div key={stat.id} className={styles.statCard}>
                  <div className={styles.statIcon}>
                    <Icon size={18} />
                  </div>
                  <p className={styles.statValue}>{stat.value}</p>
                  <p className={styles.statLabel}>{stat.label}</p>
                </div>
              );
            })}
          </div>

          <h2 className={styles.sectionTitle}>Continue Where You Left Off</h2>
          <div className={styles.continueCard}>
            <div className={styles.continueIcon}>
              <BookOpen size={20} />
            </div>
            {hasCurrentLesson ? (
              <>
                <div className={styles.continueInfo}>
                  <p className={styles.continueMeta}>Up next</p>
                  <p className={styles.continueTitle}>{currentLesson.topic.title}</p>
                  <p className={styles.continueSubmeta}>{currentLesson.week.label}</p>
                </div>
                <Button onClick={goToLesson}>Continue</Button>
              </>
            ) : (
              <div className={styles.continueInfo}>
                <p className={styles.continueTitle}>You're all caught up! 🎉</p>
                <p className={styles.continueSubmeta}>No lessons left to complete right now.</p>
              </div>
            )}
          </div>

          <h2 className={styles.sectionTitle}>Enrolled Subjects</h2>
          <div className={styles.subjectsList}>
            {enrolledSubjects.map((subject) => (
              <div key={subject.id} className={styles.subjectRow}>
                <span className={styles.subjectName}>{subject.name}</span>
                <span className={styles.subjectProgress}>{subject.progress}%</span>
              </div>
            ))}
          </div>
        </main>
      </div>
    </div>
  );
}