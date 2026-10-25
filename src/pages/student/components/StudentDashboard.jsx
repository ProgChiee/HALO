import StudyCalendar from './StudyCalendar';
import { useStudentWorkflow } from '../../../context/student/useStudentWorkflow';
import { logStudentError } from '../../../utils/studentDiagnostics';
import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { LayoutGrid, BookOpen, Award, CheckCircle2, TrendingUp, Flame } from 'lucide-react';
import StudentPageShell from './StudentPageShell';
import Button from '../../../components/shared/Button';
import { useAuth } from '../../../context/login/useAuth';
import { selectContinueLesson } from '../../../utils/continueLearning';
import styles from '../styles/StudentDashboard.module.css';

// Resume persisted activity across eligible subjects, with a stable no-history fallback.
export default function StudentDashboard() {
  const workflow = useStudentWorkflow();
  const { getDashboardData, getSubjectsData, getSubjectWeeks } = workflow.api;
  const { user } = useAuth();
  const navigate = useNavigate();
  const firstName = (workflow.target ?? user)?.name?.split(' ')[0] ?? 'there';

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
  }, [statsRetry, getDashboardData]);

  useEffect(() => {
    const controller = new AbortController();

    getSubjectsData({ signal: controller.signal }).then(data => {
      if (!controller.signal.aborted) { setSubjects(data); setSubjectsStatus('ready'); }
    }).catch(error => {
      if (!controller.signal.aborted) { logStudentError('load-subjects', error); setSubjectsStatus('error'); }
    });
    return () => controller.abort();
  }, [subjectsRetry, getSubjectsData]);

  useEffect(() => {
    if (subjectsStatus !== 'ready') return;
    const controller = new AbortController();

    const candidates = subjects;
    Promise.allSettled(candidates.map(subject => getSubjectWeeks(subject.subjectId, { signal: controller.signal })))
      .then(results => {
        if (controller.signal.aborted) return;
        const groups = [];
        for (let i = 0; i < results.length; i++) {
          if (results[i].status === 'rejected') {
            logStudentError('load-continue', results[i].reason);
            setNextLesson(null); setContinueStatus('error'); return;
          }
          groups.push({ subject: candidates[i], weeks: results[i].value });
        }
        setNextLesson(selectContinueLesson(groups));
        setContinueStatus('ready');
      });
    return () => controller.abort();
  }, [subjects, subjectsStatus, continueRetry, getSubjectWeeks]);

  const statCards = summary ? [
    { id: 'completedModules', icon: CheckCircle2, value: summary.completedModules, label: 'Modules completed' },
    { id: 'passedAssessments', icon: TrendingUp, value: summary.passedAssessments, label: 'Assessments passed' },
    { id: 'totalBadges', icon: Award, value: summary.totalBadges, label: 'Badges earned' },
    { id: 'latestScore', icon: BookOpen, value: summary.latestAssessmentScore == null ? 'No assessment yet' : `${summary.latestAssessmentScore}%`, empty: summary.latestAssessmentScore == null, label: 'Latest assessment score' },
  ] : [];

  const nextSubject = nextLesson?.subject;

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
                  <p className={stat.empty ? styles.statEmptyValue : styles.statValue}>{stat.value}</p>
                  <p className={styles.statLabel}>{stat.label}</p>
                </div>
              );
            })}
          </div>}

          {statsStatus === 'ready' && summary?.studyActivity && <section className={styles.studySection} aria-labelledby="study-activity-title">
            <h2 id="study-activity-title" className={styles.sectionTitle}>My Study Activity</h2>
            <div className={styles.studyActivityGrid}>
              <div className={styles.activityCard}>
                <h3 className={styles.activityLabel}>Weekly Study</h3>
                <p className={styles.activityValue}>{summary.studyActivity.weeklyStudyDays} / 7 active days</p>
                <div className={styles.studyDots} aria-hidden="true">{Array.from({length:7},(_,i)=><span key={i} className={i<summary.studyActivity.weeklyStudyDays ? styles.studyDotActive : styles.studyDot} />)}</div>
              </div>
              <div className={styles.activityCard}>
                <h3 className={styles.activityLabel}><Flame size={16} aria-hidden="true" />Study Streak</h3>
                <p className={styles.activityValue}>{summary.studyActivity.studyStreak}-day streak</p>
                <p className={styles.activityCaption} title="Yesterday keeps your streak going until today ends.">Keep studying to continue your streak.</p>
              </div>
              <div className={styles.activityCard}>
                <h3 className={styles.activityLabel}>AI Mentor Sessions</h3>
                <div className={styles.mentorStats}>
                  <p><strong>{summary.studyActivity.mentorSessionsWeek ?? 0}</strong>{' '}<span>this week</span></p>
                  <p><strong>{summary.studyActivity.mentorSessionsMonth ?? 0}</strong>{' '}<span>this month</span></p>
                </div>
              </div>
            </div>
            <StudyCalendar />
            <p className={styles.activityHelp}>Week starts Monday. Dates use {summary.studyActivity.timezone}. Lesson study, assessments, completions, and real Mentor messages count; Preview activity does not. Yesterday keeps your streak going until today ends.</p>
          </section>}

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
                      ? navigate(`${workflow.basePath}/lesson/${nextLesson.subject.subjectId}/${nextLesson.week.weekId}`)
                      : navigate(workflow.basePath + '/subjects')
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
                <span className={styles.subjectProgress}>Progress: {subject.progressPercentage}%</span>
                <progress className={styles.subjectBar} max="100" value={subject.progressPercentage} aria-label={`${subject.subjectName} progress`} />
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
