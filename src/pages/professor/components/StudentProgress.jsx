import { TrendingUp, CheckCircle2, Award, Users } from 'lucide-react';
import PageShell from '../../../components/shared/PageShell';
import { PROFESSOR_NAV_ITEMS } from '../../../data/navigationData';
import { getStudents, getStudentProgress } from '../../../services/professor/professorService';
import { useRemoteData } from '../../../hooks/useRemoteData';
import { mapWithConcurrency } from '../../../utils/asyncPool';
import styles from '../styles/StudentProgress.module.css';

async function loadStudentRows(config) {
  const students = await getStudents(config);
  return mapWithConcurrency(students, 5, async (student) => {
    try {
      const progress = await getStudentProgress(student.userId, config);
      return { ...student, ...progress };
    } catch (error) {
      if (config.signal.aborted) throw error;
      return { ...student, progressUnavailable: true };
    }
  }, config.signal);
}

export default function StudentProgress() {
  const { data: rows, isLoading, error: loadError, reload } = useRemoteData(loadStudentRows, []);

  if (isLoading) {
    return (
      <PageShell navItems={PROFESSOR_NAV_ITEMS} sectionLabel="Prof" roleBadge="Professor">
        <p className={styles.loadingText}>Loading student progress...</p>
      </PageShell>
    );
  }

  if (loadError) {
    return (
      <PageShell navItems={PROFESSOR_NAV_ITEMS} sectionLabel="Prof" roleBadge="Professor">
        <p className={styles.loadingText}>Couldn't load student progress. Please refresh and try again.</p>
      </PageShell>
    );
  }

  const totalStudents = rows.length;
  const totalCompletedModules = rows.reduce((sum, r) => sum + (r.completedModules ?? 0), 0);
  const totalPassedAssessments = rows.reduce((sum, r) => sum + (r.passedAssessments ?? 0), 0);

  return (
    <PageShell navItems={PROFESSOR_NAV_ITEMS} sectionLabel="Prof" roleBadge="Professor">
      <header className={styles.topbar}>
        <div className={styles.breadcrumb}>
          <TrendingUp size={16} />
          Student Progress
        </div>
      </header>

      <main className={styles.main}>
        <div className={styles.statsGrid}>
          <div className={styles.statCard}>
            <div className={styles.statIcon}><Users size={18} /></div>
            <p className={styles.statValue}>{totalStudents}</p>
            <p className={styles.statLabel}>Students</p>
          </div>
          <div className={styles.statCard}>
            <div className={styles.statIcon}><CheckCircle2 size={18} /></div>
            <p className={styles.statValue}>{totalCompletedModules}</p>
            <p className={styles.statLabel}>Modules completed (total)</p>
          </div>
          <div className={styles.statCard}>
            <div className={styles.statIcon}><Award size={18} /></div>
            <p className={styles.statValue}>{totalPassedAssessments}</p>
            <p className={styles.statLabel}>Assessments passed (total)</p>
          </div>
        </div>

        <div className={styles.tableCard}>
          {rows.some((row) => row.progressUnavailable) && <p className={styles.emptyState}>
            Some progress records could not load. Totals include loaded records only.
            <button onClick={reload}>Retry</button>
          </p>}
          <div className={styles.tableHeaderRow}>
            <span>Student</span>
            <span>Section</span>
            <span>Completed Modules</span>
            <span>Assessments Passed</span>
            <span>Badges</span>
          </div>

          {rows.map((student) => (
            <div key={student.userId} className={styles.tableRow}>
              <span className={styles.studentName}>{student.name}</span>
              <span>{student.section}</span>
              <span className={styles.completedModules}>{student.completedModules ?? 'Unavailable'}</span>
              <span>{student.passedAssessments ?? 'Unavailable'}</span>
              <span>{student.totalBadges ?? 'Unavailable'}</span>
            </div>
          ))}

          {rows.length === 0 && (
            <p className={styles.emptyState}>No students enrolled yet.</p>
          )}
        </div>
      </main>
    </PageShell>
  );
}
