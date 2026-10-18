import { professorErrorMessage } from '../../../utils/professorErrors';
import { useCallback, useState } from 'react';
import { TrendingUp, CheckCircle2, Award, Users } from 'lucide-react';
import PageShell from './ProfessorPageShell';
import { PROFESSOR_NAV_ITEMS } from '../../../data/navigationData';
import { useProfessorWorkflow } from '../../../context/professor/useProfessorWorkflow';
import { useRemoteData } from '../../../hooks/useRemoteData';
import styles from '../styles/StudentProgress.module.css';
import LearningEngagement from './LearningEngagement';

export default function StudentProgress() {
  const [page, setPage] = useState(0);
  const [view, setView] = useState('CURRENT');
  // A new page unmounts the previous request state immediately.
  return <StudentProgressPage key={`${view}-${page}`} page={page} view={view} onViewChange={next => { setView(next); setPage(0); }} onPageChange={setPage} />;
}

function StudentProgressPage({ page, onPageChange, view, onViewChange }) {
  const workflow = useProfessorWorkflow();
  const { getStudentProgressSummaries } = workflow.api;

  const loadPage = useCallback((config) => getStudentProgressSummaries(page, 20, { ...config, params: { view } }), [page, view, getStudentProgressSummaries]);
  const { data, isLoading, error: loadError, reload } = useRemoteData(loadPage);

  if (isLoading) {
    return (
      <PageShell responsive navItems={PROFESSOR_NAV_ITEMS} sectionLabel="Prof" roleBadge="Professor">
        <p className={styles.loadingText}>Loading student progress...</p>
      </PageShell>
    );
  }

  if (loadError) {
    return (
      <PageShell responsive navItems={PROFESSOR_NAV_ITEMS} sectionLabel="Prof" roleBadge="Professor">
        <p className={styles.loadingText}>{professorErrorMessage(loadError, "Couldn't load student progress. Please try again.")}</p>
        <button onClick={reload}>Retry</button>
        {page > 0 && <button onClick={() => onPageChange(page - 1)}>Previous page</button>}
      </PageShell>
    );
  }

  const rows = data.content;
  const totalStudents = data.totalElements;
  const totalCompletedModules = rows.reduce((sum, r) => sum + (r.completedModules ?? 0), 0);
  const totalPassedAssessments = rows.reduce((sum, r) => sum + (r.passedAssessments ?? 0), 0);

  return (
    <PageShell responsive navItems={PROFESSOR_NAV_ITEMS} sectionLabel="Prof" roleBadge="Professor">
      <header className={styles.topbar}>
        <div className={styles.breadcrumb}>
          <TrendingUp size={16} />
          Student Progress
        </div>
      </header>

      <main className={styles.main}>
        <div className={styles.viewControls} role="group" aria-label="Student population">
          <button className={styles.compactButton} aria-pressed={view === 'CURRENT'} onClick={() => onViewChange('CURRENT')}>Current Students</button>
          <button className={styles.compactButton} aria-pressed={view === 'FORMER'} onClick={() => onViewChange('FORMER')}>Former / Archived</button>
        </div>
        {view === 'FORMER' && <p className={styles.engagementHelp}>Read-only history for inactive and archived students in your subject scope.</p>}
        <div className={styles.statsGrid}>
          <div className={styles.statCard}>
            <div className={styles.statIcon}><Users size={18} /></div>
            <p className={styles.statValue}>{totalStudents}</p>
            <p className={styles.statLabel}>{view === 'CURRENT' ? 'Current students in your subjects' : 'Former students in your subjects'}</p>
          </div>
          <div className={styles.statCard}>
            <div className={styles.statIcon}><CheckCircle2 size={18} /></div>
            <p className={styles.statValue}>{totalCompletedModules}</p>
            <p className={styles.statLabel}>Modules completed (this page)</p>
          </div>
          <div className={styles.statCard}>
            <div className={styles.statIcon}><Award size={18} /></div>
            <p className={styles.statValue}>{totalPassedAssessments}</p>
            <p className={styles.statLabel}>Assessments passed (this page)</p>
          </div>
        </div>

        <div className={styles.tableCard} role="region" aria-label="Student progress table, scroll horizontally to view all columns" tabIndex={0}>
          <table className={styles.progressTable} aria-label="Student progress"><thead><tr className={styles.tableHeaderRow}>
            <th scope="col">Student</th>
            <th scope="col">Section</th>
            <th scope="col">Completed Modules</th>
            <th scope="col">Assessments Passed</th>
            <th scope="col">Institution-wide Achievements</th>
          </tr></thead><tbody>

          {rows.map((student) => (
            <tr key={student.userId} className={styles.tableRow}>
              <td className={styles.studentName}>{student.name}{view === 'FORMER' && <span className={styles.statusBadge}>{student.status === 'ARCHIVED' ? 'Archived' : 'Inactive'}</span>}</td>
              <td>{student.section || 'Not assigned'}</td>
              <td className={styles.completedModules}>{student.completedModules ?? 'Unavailable'}</td>
              <td>{student.passedAssessments ?? 'Unavailable'}</td>
              <td>{student.totalBadges ?? 'Unavailable'}</td>
            </tr>
          ))}

          {rows.length === 0 && (
            <tr><td colSpan={5} className={styles.emptyState}>{view === 'FORMER' ? 'No former or archived students in your subject scope.' : 'No current students in your subject scope.'}</td></tr>
          )}
          </tbody></table>
        </div>
        <nav aria-label="Student progress pagination">
          <button disabled={data.first} onClick={() => onPageChange(page - 1)}>Previous</button>
          <span> Page {data.number + 1} of {Math.max(1, data.totalPages)} </span>
          <button disabled={data.last} onClick={() => onPageChange(page + 1)}>Next</button>
        </nav>
        <LearningEngagement view={view} />
      </main>
    </PageShell>
  );
}
