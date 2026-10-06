import { logStudentError } from '../../../utils/studentDiagnostics';
import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { LayoutGrid, BookOpen, Award, CheckCircle2, TrendingUp } from 'lucide-react';
import StudentPageShell from './StudentPageShell';
import Button from '../../../components/shared/Button';
import { useAuth } from '../../../context/login/useAuth';
import { getDashboardData, getSubjectsData, getSubjectWeeks } from '../../../services/student/studentService';
import { findAvailableWeek } from '../../../utils/backendContract';
import styles from '../styles/StudentDashboard.module.css';

// Find the first published, unlocked, incomplete week across subjects.
export default function StudentDashboard() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const firstName = user?.name?.split(' ')[0] ?? 'there';

  const [summary, setSummary] = useState(null);
  const [subjects, setSubjects] = useState([]);
  const [nextLesson, setNextLesson] = useState(null);
  const [statsStatus, setStatsStatus] = useState('loading');
  const [subjectsStatus, setSubjectsStatus] = useState('loading');
  const [continueStatus, setContinueStatus] = useState('loading');
  const [statsRetry, setStatsRetry] = useState(0);
  const [subjectsRetry, setSubjectsRetry] = useState(0);
  const [continueRetry, setContinueRetry] = useState(0);

  useEffect(() => {
    const controller = new AbortController();

    getDashboardData({ signal: controller.signal }).then(data => {
      if (!controller.signal.aborted) { setSummary(data); setStatsStatus('ready'); }
    }).catch(error => {
      if (!controller.signal.aborted) { logStudentError('load-dashboard', error); setStatsStatus('error'); }
    });
    return () => controller.abort();
  }, [statsRetry]);

  useEffect(() => {
    const controller = new AbortController();

    getSubjectsData({ signal: controller.signal }).then(data => {
      if (!controller.signal.aborted) { setSubjects(data); setSubjectsStatus('ready'); }
    }).catch(error => {
      if (!controller.signal.aborted) { logStudentError('load-subjects', error); setSubjectsStatus('error'); }
    });
    return () => controller.abort();
  }, [subjectsRetry]);

  useEffect(() => {
    if (subjectsStatus !== 'ready') return;
    const controller = new AbortController();

    const candidates = subjects.filter(subject => subject.progressPercentage < 100);
    Promise.allSettled(candidates.map(subject => getSubjectWeeks(subject.subjectId, { signal: controller.signal })))
      .then(results => {
        if (controller.signal.aborted) return;
        let failed = false;
        for (let i = 0; i < results.length; i++) {
          if (results[i].status === 'rejected') {
            failed = true; logStudentError('load-continue', results[i].reason); continue;
          }
          const week = findAvailableWeek(results[i].value);
          if (week) { setNextLesson({ subject: candidates[i], week }); setContinueStatus('ready'); return; }
        }
        setContinueStatus(failed ? 'error' : 'ready');
      });
    return () => controller.abort();
  }, [subjects, subjectsStatus, continueRetry]);

  const statCards = summary ? [
    { id: 'completedModules', icon: CheckCircle2, value: summary.completedModules, label: 'Modules completed' },
    { id: 'passedAssessments', icon: TrendingUp, value: summary.passedAssessments, label: 'Assessments passed' },
    { id: 'totalBadges', icon: Award, value: summary.totalBadges, label: 'Badges earned' },
    { id: 'latestScore', icon: BookOpen, value: `${summary.latestAssessmentScore ?? 0}%`, label: 'Latest assessment score' },
  ] : [];

  const nextSubject = subjects.find((s) => s.progressPercentage < 100);

  return (
    <StudentPageShell>

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

          {statsStatus === 'loading' && <p role="status">Loading dashboard statistics...</p>}
          {statsStatus === 'error' && <div role="alert"><p>Couldn't load your dashboard statistics.</p><Button onClick={() => { setStatsStatus('loading'); setStatsRetry(value => value + 1); }}>Retry statistics</Button></div>}
          {statsStatus === 'ready' && <div className={styles.statsGrid}>
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
          </div>}

          <h2 className={styles.sectionTitle}>Continue Where You Left Off</h2>
          <div className={styles.continueCard}>
            <div className={styles.continueIcon}>
              <BookOpen size={20} />
            </div>
            {subjectsStatus === 'loading' || (subjectsStatus === 'ready' && continueStatus === 'loading') ? <p role="status">Finding your next lesson...</p>
              : subjectsStatus === 'error' ? <p>Continue is unavailable until Subjects loads.</p>
              : continueStatus === 'error' ? <div role="alert"><p>Couldn't load your next lesson.</p><Button onClick={() => { setContinueStatus('loading'); setNextLesson(null); setContinueRetry(value => value + 1); }}>Retry Continue</Button></div>
              : nextSubject ? (
              <>
                <div className={styles.continueInfo}>
                  <p className={styles.continueMeta}>Up next</p>
                  <p className={styles.continueTitle}>
                    {nextLesson ? `Week ${nextLesson.week.weekNumber}: ${nextLesson.week.title}` : nextSubject.subjectName}
                  </p>
                  <p className={styles.continueSubmeta}>
                    {nextLesson ? nextLesson.subject.subjectName : `${nextSubject.completedWeeks}/${nextSubject.totalWeeks} weeks completed`}
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
          {subjectsStatus === 'loading' && <p role="status">Loading subjects...</p>}
          {subjectsStatus === 'error' && <div role="alert"><p>Couldn't load your subjects.</p><Button onClick={() => { setSubjectsStatus('loading'); setContinueStatus('loading'); setNextLesson(null); setSubjectsRetry(value => value + 1); }}>Retry subjects</Button></div>}
          {subjectsStatus === 'ready' && <div className={styles.subjectsList}>
            {subjects.map((subject) => (
              <div key={subject.subjectId} className={styles.subjectRow}>
                <span className={styles.subjectName}>{subject.subjectName}</span>
                <span className={styles.subjectProgress}>{subject.progressPercentage}%</span>
              </div>
            ))}
            {subjects.length === 0 && (
              <p className={styles.emptyState}>No subjects enrolled yet.</p>
            )}
          </div>}
        </main>
      </div>
    </StudentPageShell>
  );
}
