import { useState, useEffect } from 'react';
import { TrendingUp, CheckCircle2, Award, Users } from 'lucide-react';
import PageShell from '../../../components/shared/PageShell';
import { PROFESSOR_NAV_ITEMS } from '../../../data/navigationData';
import { getStudents, getStudentProgress } from '../../../services/professor/professorService';
import styles from '../styles/StudentProgress.module.css';

// ⚠️ Redesigned for the real backend. Two differences from the old mock:
//
// 1. No single "give me everyone's progress" endpoint exists — we fetch
//    the student list, then fetch each student's progress individually
//    (N+1 requests). Fine for a small class; if this list grows large,
//    ask your backend team for a bulk endpoint instead
//    (e.g. GET /professor/students/progress returning all of them at once).
//
// 2. The backend's ProfessorStudentProgressResponse only has
//    completedModules, passedAssessments, and totalBadges — there's no
//    quiz average score, pass/fail counts, or an overall progress %
//    the old mock UI showed. Those fields don't exist yet; the table
//    below only shows what the backend actually provides.

export default function StudentProgress() {
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [rows, setRows] = useState([]);

  useEffect(() => {
    let isMounted = true;

    async function loadData() {
      try {
        const students = await getStudents();
        const withProgress = await Promise.all(
          students.map(async (student) => {
            const progress = await getStudentProgress(student.userId);
            return { ...student, ...progress };
          })
        );
        if (isMounted) setRows(withProgress);
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
              <span className={styles.completedModules}>{student.completedModules}</span>
              <span>{student.passedAssessments}</span>
              <span>{student.totalBadges}</span>
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