import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { LayoutGrid, BookOpen, Award, CheckCircle2, TrendingUp } from 'lucide-react';
import Sidebar from '../../../components/shared/Sidebar';
import Button from '../../../components/shared/Button';
import { useAuth } from '../../../context/login/AuthContext';
import { STUDENT_NAV_ITEMS } from '../../../data/navigationData';
import { getDashboardData, getSubjectsData, getSubjectWeeks } from '../../../services/student/studentService';
import styles from '../styles/StudentDashboard.module.css';

// ✅ Redesigned for the real backend shapes (StudentDashboardController +
// StudentLearningProgressionController).
//
// 1. getDashboardData() returns a flat summary — { completedModules,
//    totalBadges, passedAssessments, latestAssessmentScore }.
//
// 2. There's still no dedicated "current week" shortcut endpoint, but
//    "Continue Where You Left Off" now finds the actual next lesson
//    itself: it takes the first not-yet-completed subject, then fetches
//    that subject's weeks and picks the first one that's unlocked but not
//    completed — and deep-links straight into it, instead of just
//    pointing at the Subjects list.

export default function StudentDashboard() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const firstName = user?.name?.split(' ')[0] ?? 'there';

  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [summary, setSummary] = useState(null); // StudentDashboardResponse
  const [subjects, setSubjects] = useState([]); // StudentSubjectResponse[]
  const [nextLesson, setNextLesson] = useState(null); // { subject, week } | null

  useEffect(() => {
    let isMounted = true;

    async function loadData() {
      try {
        const [dashboardSummary, subjectList] = await Promise.all([
          getDashboardData(),
          getSubjectsData(),
        ]);
        if (!isMounted) return;
        setSummary(dashboardSummary);
        setSubjects(subjectList);

        // Find the actual next lesson: first incomplete subject, then its
        // first unlocked-but-not-completed week.
        const incompleteSubject = subjectList.find((s) => s.progressPercentage < 100);
        if (incompleteSubject) {
          const weeks = await getSubjectWeeks(incompleteSubject.subjectId);
          if (!isMounted) return;
          const upNextWeek = weeks.find((w) => w.unlocked && !w.completed);
          if (upNextWeek) {
            setNextLesson({ subject: incompleteSubject, week: upNextWeek });
          }
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

  if (loadError || !summary) {
    return (
      <div className={styles.layout}>
        <Sidebar navItems={STUDENT_NAV_ITEMS} progress={null} />
        <div className={styles.contentArea}>
          <p className={styles.loadingText}>Couldn't load your dashboard. Please refresh and try again.</p>
        </div>
      </div>
    );
  }

  const statCards = [
    { id: 'completedModules', icon: CheckCircle2, value: summary.completedModules, label: 'Modules completed' },
    { id: 'passedAssessments', icon: TrendingUp, value: summary.passedAssessments, label: 'Assessments passed' },
    { id: 'totalBadges', icon: Award, value: summary.totalBadges, label: 'Badges earned' },
    { id: 'latestScore', icon: BookOpen, value: `${summary.latestAssessmentScore ?? 0}%`, label: 'Latest assessment score' },
  ];

  const nextSubject = subjects.find((s) => s.progressPercentage < 100);

  return (
    <div className={styles.layout}>
      <Sidebar navItems={STUDENT_NAV_ITEMS} progress={null} />

      <div className={styles.contentArea}>
        <header className={styles.topbar}>
          <div className={styles.breadcrumb}>
            <LayoutGrid size={16} />
            Dashboard
          </div>
        </header>

        <main className={styles.main}>
          <h1 className={styles.greeting}>Welcome back, {firstName}! 👋</h1>
          <p className={styles.subtext}>Here's your learning overview for today</p>

          <div className={styles.statsGrid}>
            {statCards.map((stat) => {
              const Icon = stat.icon;
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
            {nextSubject ? (
              <>
                <div className={styles.continueInfo}>
                  <p className={styles.continueMeta}>Up next</p>
                  <p className={styles.continueTitle}>
                    {nextLesson ? `Week ${nextLesson.week.weekNumber}: ${nextLesson.week.title}` : nextSubject.subjectName}
                  </p>
                  <p className={styles.continueSubmeta}>
                    {nextLesson ? nextSubject.subjectName : `${nextSubject.completedWeeks}/${nextSubject.totalWeeks} weeks completed`}
                  </p>
                </div>
                <Button
                  onClick={() =>
                    nextLesson
                      ? navigate(`/student/lesson/${nextLesson.subject.subjectId}/${nextLesson.week.weekId}`)
                      : navigate('/student/subjects')
                  }
                >
                  Continue
                </Button>
              </>
            ) : (
              <div className={styles.continueInfo}>
                <p className={styles.continueTitle}>You're all caught up! 🎉</p>
                <p className={styles.continueSubmeta}>No subjects left to complete right now.</p>
              </div>
            )}
          </div>

          <h2 className={styles.sectionTitle}>Enrolled Subjects</h2>
          <div className={styles.subjectsList}>
            {subjects.map((subject) => (
              <div key={subject.subjectId} className={styles.subjectRow}>
                <span className={styles.subjectName}>{subject.subjectName}</span>
                <span className={styles.subjectProgress}>{subject.progressPercentage}%</span>
              </div>
            ))}
            {subjects.length === 0 && (
              <p className={styles.emptyState}>No subjects enrolled yet.</p>
            )}
          </div>
        </main>
      </div>
    </div>
  );
}