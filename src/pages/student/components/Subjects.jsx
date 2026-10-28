import Topbar from '../../../components/shared/Topbar';
import { useStudentWorkflow } from '../../../context/student/useStudentWorkflow';
import { logStudentError } from '../../../utils/studentDiagnostics';
import { useState, useEffect } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { ChevronDown, ChevronUp, CheckCircle2, Play, Lock } from 'lucide-react';
import StudentPageShell from './StudentPageShell';
import { useSubjectWeeks } from '../../../hooks/useSubjectWeeks';
import styles from '../styles/Subjects.module.css';
import { yearLabels } from '../../../utils/registration';
import AcademicProfileNotice from './AcademicProfileNotice';

// Keep old subject-specific bookmarks; the plain catalog now belongs to My Subjects.
export default function Subjects() {
  const workflow = useStudentWorkflow();
  const [params] = useSearchParams();
  const requestedSubject = params.get('subject');
  if (requestedSubject === null) return <Navigate to={`${workflow.basePath}/subjects/my`} replace />;
  return <SubjectLearning key={`${workflow.identity ?? workflow.basePath}:${requestedSubject}`} requestedSubject={requestedSubject} />;
}

function SubjectLearning({ requestedSubject }) {
  const workflow = useStudentWorkflow();
  const { getSubjectsData, getSubjectWeeks } = workflow.api;
  const navigate = useNavigate();
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [reload, setReload] = useState(0);
  const [subject, setSubject] = useState(null);
  const [expanded, setExpanded] = useState(true);
  const { weeksBySubject, weeksLoading, weekErrors, loadWeeks } = useSubjectWeeks(getSubjectWeeks);

  useEffect(() => {
    const controller = new AbortController();
    async function loadData() {
      setIsLoading(true);
      setLoadError(false);
      try {
        const data = await getSubjectsData({ signal: controller.signal });
        if (controller.signal.aborted) return;
        const selected = data.find(item => String(item.subjectId) === requestedSubject) ?? null;
        setSubject(selected);
        if (selected?.enrolled === true) loadWeeks(selected.subjectId);
      } catch (error) {
        if (!controller.signal.aborted) {
          logStudentError('load-subjects', error);
          setLoadError(error?.response?.data?.code === 'PROFILE_INCOMPLETE' ? 'PROFILE_INCOMPLETE' : true);
        }
      } finally {
        if (!controller.signal.aborted) setIsLoading(false);
      }
    }
    loadData();
    return () => controller.abort();
  }, [getSubjectsData, reload, requestedSubject, loadWeeks]);

  async function toggleSubject() {
    setExpanded(value => !value);
    if (!expanded && !weeksBySubject[subject.subjectId]) await loadWeeks(subject.subjectId);
  }

  const weeks = subject ? weeksBySubject[subject.subjectId] : null;
  const isFullyCompleted = subject?.totalWeeks > 0 && subject.completedWeeks === subject.totalWeeks;

  return <StudentPageShell>
    <Topbar title="Subjects" />
    <main className={styles.main}>
      <nav aria-label="Learning navigation"><Link className={styles.backLink} to={`${workflow.basePath}/subjects/my`}>Back to My Subjects</Link></nav>
      {isLoading ? <p className={styles.loadingText} role="status">Loading subjects...</p>
        : loadError === 'PROFILE_INCOMPLETE' ? <AcademicProfileNotice onRefresh={() => setReload(value => value + 1)} />
        : loadError ? <p className={styles.loadingText}>Couldn't load subjects. Please refresh and try again.</p>
        : !subject ? <p className={styles.emptyState}>This subject is not available to you. Return to My Subjects to choose another subject.</p>
        : subject.enrolled !== true ? <section aria-label="Enrollment required"><p className={styles.subtitle}>Enroll in this subject to access learning activities.</p><Link className={styles.backLink} to={`${workflow.basePath}/subjects/browse`}>Browse Subjects</Link></section>
        : <>
          <p className={styles.subtitle}>Choose a weekly lesson for this subject.</p>
          <section className={styles.topicWrapper}>
            <button type="button" aria-expanded={expanded} aria-controls={expanded ? `subject-weeks-${subject.subjectId}` : undefined}
              className={`${styles.topicRow} ${expanded ? styles.topicRowExpanded : ''}`} onClick={toggleSubject}>
              <span className={styles.topicInfo}>
                <span className={styles.topicTitle}>{subject.subjectName}</span>
                <span className={styles.topicMeta}>
                  {[subject.subjectCode, yearLabels[subject.yearLevel] ?? subject.yearLevel, subject.totalWeeks != null ? `${subject.totalWeeks} ${subject.totalWeeks === 1 ? 'week' : 'weeks'}` : null].filter(Boolean).map((value,index) => <span key={index}>{value}</span>)}
                  {isFullyCompleted && <span className={styles.completedLabel}>Completed</span>}
                </span>
              </span>
              {expanded ? <ChevronUp size={18} className={styles.chevron} /> : <ChevronDown size={18} className={styles.chevron} />}
            </button>
            {expanded && <div id={`subject-weeks-${subject.subjectId}`} className={styles.weeksPanel}>
              <div className={styles.weeksPanelHeader}><h2>Weekly lessons</h2>{subject.progressPercentage != null && <span className={styles.topicMeta}>{subject.progressPercentage}% learning progress</span>}</div>
              {weeks?.length === 0 && !weeksLoading[subject.subjectId] && !weekErrors[subject.subjectId] && <p className={styles.emptyState}>No weekly lessons are available yet.</p>}
              {weeks?.length > 0 && !weeksLoading[subject.subjectId] && !weekErrors[subject.subjectId] && !weeks.some(week => week.lessonAvailable && week.unlocked) && <p className={styles.emptyState}>No lessons are currently available to open in this subject.</p>}
              {weeksLoading[subject.subjectId] && !weeks && <p className={styles.loadingText}>Loading weeks...</p>}
              {weekErrors[subject.subjectId] && <p className={styles.loadingText}>Couldn't load weeks. Collapse and reopen to retry.</p>}
              {weeks?.map(week => {
                const status = !week.lessonAvailable || !week.unlocked ? 'locked' : week.completed ? 'done' : 'now';
                const WeekIcon = status === 'done' ? CheckCircle2 : status === 'now' ? Play : Lock;
                return <button key={week.weekId} type="button" disabled={status === 'locked'}
                  className={`${styles.weekRow} ${status === 'locked' ? styles.weekRowLocked : ''}`}
                  onClick={() => { if (status !== 'locked') navigate(`${workflow.basePath}/lesson/${subject.subjectId}/${week.weekId}`); }}>
                  <span className={`${styles.weekIcon} ${status === 'locked' ? styles.weekIcon_locked : ''}`}><WeekIcon size={16} /></span>
                  <span className={styles.weekLabel}><span className={styles.weekNumber}>Week {week.weekNumber}</span><span>{week.title}</span></span>
                  <span className={`${styles.weekBadge} ${styles[`weekBadge_${status}`]}`}>{!week.lessonAvailable ? 'Not published' : status === 'done' ? 'Completed' : status === 'now' ? 'Available' : 'Locked'}</span>
                  {status !== 'locked' && <span className={styles.weekAction}>{status === 'done' ? 'Review lesson' : 'Open lesson'}</span>}
                </button>;
              })}
            </div>}
          </section>
        </>}
    </main>
  </StudentPageShell>;
}
