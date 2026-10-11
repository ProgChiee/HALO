import { useStudentWorkflow } from '../../../context/student/useStudentWorkflow';
import { logStudentError } from '../../../utils/studentDiagnostics';
import { useState, useEffect } from 'react';
import { CheckCircle2, BookOpen } from 'lucide-react';
import { Link } from 'react-router-dom';
import StudentPageShell from './StudentPageShell';
import styles from '../styles/Progress.module.css';

// Module totals and completion counts use the same eligible-subject summaries.
export default function Progress() {
  const workflow = useStudentWorkflow();
  const { getSubjectsData } = workflow.api;
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);

  const [subjects, setSubjects] = useState([]);

  useEffect(() => {
    let isMounted = true;

    async function loadData() {
      try {
        const subjectList = await getSubjectsData();
        if (isMounted) {

          setSubjects(subjectList);
        }
      } catch (err) {
        logStudentError('load-progress', err);
        if (isMounted) setLoadError(true);
      } finally {
        if (isMounted) setIsLoading(false);
      }
    }

    loadData();
    return () => { isMounted = false; };
  }, [getSubjectsData]);

  if (isLoading) {
    return (
      <StudentPageShell>
        <div className={styles.contentArea}>
          <p className={styles.loadingText}>Loading your progress...</p>
        </div>
      </StudentPageShell>
    );
  }

  if (loadError) {
    return (
      <StudentPageShell>
        <div className={styles.contentArea}>
          <p className={styles.loadingText}>Couldn't load your progress. Please refresh and try again.</p>
        </div>
      </StudentPageShell>
    );
  }

  const totalModules = subjects.reduce((sum, s) => sum + (s.eligibleModuleCount ?? 0), 0);
  const completedModules = subjects.reduce((sum, s) => sum + s.completedWeeks, 0);
  const overall = totalModules > 0 ? Math.round((completedModules / totalModules) * 100) : 0;

  return (
    <StudentPageShell>

      <div className={styles.contentArea}>
        <header className={styles.topbar}>
          <div className={styles.breadcrumb}>
            <CheckCircle2 size={16} />
            Progress
          </div>
        </header>

        <main className={styles.main}>
          <div className={styles.overviewRow}>
            <div className={styles.overallBlock}>
              <div className={styles.progressRing}>
                <svg width="140" height="140" viewBox="0 0 140 140">
                  <circle cx="70" cy="70" r="60" fill="none" stroke="var(--color-background)" strokeWidth="10" />
                  <circle
                    cx="70"
                    cy="70"
                    r="60"
                    fill="none"
                    stroke="var(--color-accent-active)"
                    strokeWidth="10"
                    strokeLinecap="round"
                    strokeDasharray={2 * Math.PI * 60}
                    strokeDashoffset={2 * Math.PI * 60 * (1 - overall / 100)}
                    transform="rotate(-90 70 70)"
                    className={styles.progressRingFill}
                  />
                </svg>
                <div className={styles.progressRingText}>
                  <p className={styles.overallPercent}>{overall}%</p>
                </div>
              </div>
              <p className={styles.overallLabel}>Overall</p>
            </div>

            <div className={styles.statsGrid}>
              <div className={styles.statCard}>
                <span className={styles.statIcon}><CheckCircle2 size={16} /></span>
                <div>
                  <p className={styles.statValue}>{completedModules}</p>
                  <p className={styles.statLabel}>Modules completed</p>
                </div>
              </div>
              <div className={styles.statCard}>
                <span className={styles.statIcon}><BookOpen size={16} /></span>
                <div>
                  <p className={styles.statValue}>{totalModules}</p>
                  <p className={styles.statLabel}>Total modules</p>
                </div>
              </div>
            </div>
          </div>

          <div className={styles.sectionHeaderRow}>
            <h2 className={styles.sectionTitle}>Subject Progress</h2>
            <Link to={workflow.basePath + '/subjects'} className={styles.viewAllLink}>View all →</Link>
          </div>
          <div className={styles.rowsList}>
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
    </StudentPageShell>
  );
}