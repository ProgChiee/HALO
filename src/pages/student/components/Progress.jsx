import Topbar from '../../../components/shared/Topbar';
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
  const [search, setSearch] = useState('');

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
      <Topbar title="Progress" />
        <div className={styles.contentArea}>
          <p className={styles.loadingText}>Loading your progress...</p>
        </div>
      </StudentPageShell>
    );
  }

  if (loadError) {
    return (
      <StudentPageShell>
      <Topbar title="Progress" />
        <div className={styles.contentArea}>
          <p className={styles.loadingText}>Couldn't load your progress. Please refresh and try again.</p>
        </div>
      </StudentPageShell>
    );
  }

  const totalModules = subjects.reduce((sum, s) => sum + (s.eligibleModuleCount ?? 0), 0);
  const completedModules = subjects.reduce((sum, s) => sum + s.completedWeeks, 0);
  const overall = totalModules > 0 ? Math.round((completedModules / totalModules) * 100) : 0;

  const query = search.trim().toLowerCase();
  const visibleSubjects = subjects.filter(subject =>
    [subject.subjectName, subject.subjectCode].some(value => (value ?? '').toLowerCase().includes(query)));

  return (
    <StudentPageShell>

      <div className={styles.contentArea}>
        <Topbar title="Progress" />

        <main className={styles.main}>
          <div className={styles.overviewRow}>
            <div className={styles.overallBlock}>
              <div className={styles.progressRing} role="progressbar" aria-label="Overall Progress" aria-valuemin={0} aria-valuemax={100} aria-valuenow={overall}>
                <svg className={styles.ringSvg} viewBox="0 0 140 140" aria-hidden="true" focusable="false">
                  <circle cx="70" cy="70" r="60" fill="none" stroke="var(--color-border)" strokeWidth="10" />
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
                  />
                </svg>
                <div className={styles.progressRingText}>
                  <p className={styles.overallPercent}>{overall}%</p>
                </div>
              </div>
              <h2 className={styles.overallLabel}>Overall Progress</h2>
            </div>

            <div className={styles.statsGrid}>
              <div className={styles.statCard}>
                <span className={styles.statIcon}><CheckCircle2 size={20} aria-hidden="true" /></span>
                  <p className={styles.statValue}>{completedModules}</p>
                  <p className={styles.statLabel}>Modules completed</p>
              </div>
              <div className={styles.statCard}>
                <span className={styles.statIcon}><BookOpen size={20} aria-hidden="true" /></span>
                  <p className={styles.statValue}>{totalModules}</p>
                  <p className={styles.statLabel}>Total modules</p>
              </div>
            </div>
          </div>

          <div className={styles.sectionHeaderRow}>
            <h2 className={styles.sectionTitle}>Subject Progress</h2>
            <Link to={workflow.basePath + '/subjects/my'} className={styles.viewAllLink}>View all subjects</Link>
          </div>
          <div className={styles.searchBar}>
            <label htmlFor="subject-progress-search" className={styles.searchLabel}>Search subjects</label>
            <div className={styles.searchControls}>
              <input id="subject-progress-search" type="search" value={search}
                placeholder="Search subjects by name or code..."
                onChange={event => setSearch(event.target.value)} />
              {search && <button type="button" onClick={() => { setSearch(''); document.getElementById('subject-progress-search')?.focus(); }}>Clear search</button>}
            </div>
          </div>
          <div className={styles.rowsList}>
            {visibleSubjects.map((subject) => (
              <div key={subject.subjectId} className={styles.subjectRow}>
                <div className={styles.subjectInfo}><h3 className={styles.subjectName}>{subject.subjectName}</h3>
                  {subject.subjectCode && <p className={styles.subjectCode}>{subject.subjectCode}</p>}</div>
                <span className={styles.subjectProgress}>{subject.progressPercentage}%</span>
                <progress className={styles.subjectBar} max={100} value={subject.progressPercentage} aria-label={`${subject.subjectName} progress`} />
              </div>
            ))}
            {subjects.length > 0 && visibleSubjects.length === 0 && <p className={styles.emptyState} role="status">No subjects found. Try another name or code.</p>}
            {subjects.length === 0 && (
              <p className={styles.emptyState}>No subjects enrolled yet.</p>
            )}
          </div>
        </main>
      </div>
    </StudentPageShell>
  );
}
