import { useState, useEffect } from 'react';
import { CheckCircle2, BookOpen } from 'lucide-react';
import { Link } from 'react-router-dom';
import Sidebar from '../../../components/shared/Sidebar';
import { STUDENT_NAV_ITEMS } from '../../../data/navigationData';
import { getProgressData, getSubjectsData } from '../../../services/student/studentService';
import styles from '../styles/Progress.module.css';

// ⚠️ Redesigned for the real backend. getProgressData() only returns a
// flat [{ moduleId, weekId, completed, completedAt }] list — no subject
// names, no quiz scores, no single "overall %" field. The per-subject
// breakdown below reuses getSubjectsData() instead (it already has
// subjectName + completedWeeks/totalWeeks per subject, which maps well to
// the old "Completed Subjects" section).
//
// The "Quiz Scores" section from the old mock is NOT included — showing
// real per-quiz history would mean calling
// quizService.getAttemptHistory(moduleId) once per completed module (an
// extra N+1 round of requests). Worth adding once the rest of the app is
// confirmed working; skipped here to keep this page's load time
// reasonable for now.

export default function Progress() {
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [completedModules, setCompletedModules] = useState([]);
  const [subjects, setSubjects] = useState([]);

  useEffect(() => {
    let isMounted = true;

    async function loadData() {
      try {
        const [progress, subjectList] = await Promise.all([
          getProgressData(),
          getSubjectsData(),
        ]);
        if (isMounted) {
          setCompletedModules(progress.filter((p) => p.completed));
          setSubjects(subjectList);
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
          <p className={styles.loadingText}>Loading your progress...</p>
        </div>
      </div>
    );
  }

  if (loadError) {
    return (
      <div className={styles.layout}>
        <Sidebar navItems={STUDENT_NAV_ITEMS} progress={null} />
        <div className={styles.contentArea}>
          <p className={styles.loadingText}>Couldn't load your progress. Please refresh and try again.</p>
        </div>
      </div>
    );
  }

  const totalWeeks = subjects.reduce((sum, s) => sum + s.totalWeeks, 0);
  const overall = totalWeeks > 0 ? Math.round((completedModules.length / totalWeeks) * 100) : 0;

  return (
    <div className={styles.layout}>
      <Sidebar navItems={STUDENT_NAV_ITEMS} progress={null} />

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
                  <circle cx="70" cy="70" r="60" fill="none" stroke="rgba(255, 255, 255, 0.08)" strokeWidth="10" />
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
                  <p className={styles.statValue}>{completedModules.length}</p>
                  <p className={styles.statLabel}>Modules completed</p>
                </div>
              </div>
              <div className={styles.statCard}>
                <span className={styles.statIcon}><BookOpen size={16} /></span>
                <div>
                  <p className={styles.statValue}>{totalWeeks}</p>
                  <p className={styles.statLabel}>Total modules</p>
                </div>
              </div>
            </div>
          </div>

          <div className={styles.sectionHeaderRow}>
            <h2 className={styles.sectionTitle}>Subject Progress</h2>
            <Link to="/student/subjects" className={styles.viewAllLink}>View all →</Link>
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
    </div>
  );
}