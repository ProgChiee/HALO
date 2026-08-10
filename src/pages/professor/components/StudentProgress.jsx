import { useState, useEffect } from 'react';
import { TrendingUp, CheckCircle2, GraduationCap } from 'lucide-react';
import PageShell from '../../../components/shared/PageShell';
import { PROFESSOR_NAV_ITEMS } from '../../../data/navigationData';
import { getStudentProgressData } from '../../../services/professor/professorService';
import styles from '../styles/StudentProgress.module.css';

// Maps the string icon names from the mock data to actual icon components
const ICONS = { TrendingUp, CheckCircle2, GraduationCap };

export default function StudentProgress() {
  const [isLoading, setIsLoading] = useState(true);
  const [data, setData] = useState(null);

  useEffect(() => {
    let isMounted = true;

    async function loadData() {
      const result = await getStudentProgressData();
      if (isMounted) {
        setData(result);
        setIsLoading(false);
      }
    }

    loadData();
    return () => { isMounted = false; };
  }, []);

  if (isLoading || !data) {
    return (
      <PageShell navItems={PROFESSOR_NAV_ITEMS} sectionLabel="Prof" roleBadge="Professor">
        <p className={styles.loadingText}>Loading student progress...</p>
      </PageShell>
    );
  }

  const { stats, students } = data;

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
          {stats.map((stat) => {
            const Icon = ICONS[stat.icon];
            return (
              <div key={stat.id} className={styles.statCard}>
                <div className={styles.statIcon}>
                  <Icon size={18} />
                </div>
                <p className={styles.statValue}>{stat.value}</p>
                <p className={styles.statLabel}>{stat.label}</p>
                <p className={styles.statSublabel}>{stat.sublabel}</p>
              </div>
            );
          })}
        </div>

        <div className={styles.tableCard}>
          <div className={styles.tableHeaderRow}>
            <span>Student</span>
            <span>Completed Modules</span>
            <span>Quiz Scores</span>
            <span>Pass / Fail</span>
            <span>Progress %</span>
          </div>

          {students.map((student) => (
            <div key={student.id} className={styles.tableRow}>
              <span className={styles.studentName}>{student.name}</span>
              <span className={styles.completedModules}>{student.completedModules}</span>

              <div className={styles.scoreCell}>
                <div className={styles.barTrack}>
                  <div className={styles.barFill} style={{ width: `${student.quizAvgScore}%` }} />
                </div>
                <span className={styles.scoreText}>{student.quizAvgScore}% avg</span>
              </div>

              <span className={styles.passFail}>
                <span className={styles.passCount}>{student.passCount} PASS</span>
                <span className={styles.failCount}>{student.failCount} FAIL</span>
              </span>

              <div className={styles.scoreCell}>
                <div className={styles.barTrack}>
                  <div className={styles.barFill} style={{ width: `${student.progressPercent}%` }} />
                </div>
                <span className={styles.scoreText}>{student.progressPercent}%</span>
              </div>
            </div>
          ))}

          {students.length === 0 && (
            <p className={styles.emptyState}>No students enrolled yet.</p>
          )}
        </div>
      </main>
    </PageShell>
  );
}