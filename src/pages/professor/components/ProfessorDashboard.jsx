import { useState, useEffect } from 'react';
import { LayoutGrid, Users, BookOpen, FileText, CheckCircle2, ClipboardCheck, Award } from 'lucide-react';
import Sidebar from '../../../components/shared/Sidebar';
import { useAuth } from '../../../context/login/useAuth';
import { PROFESSOR_NAV_ITEMS } from '../../../data/navigationData';
import { getProfessorDashboardData } from '../../../services/professor/professorService';
import styles from '../styles/ProfessorDashboard.module.css';

// ✅ Wired to the real backend — ProfessorDashboardController.
// Returns a flat summary, not the old { stats, subjects } shape.

export default function ProfessorDashboard() {
  const { user } = useAuth();
  const firstName = user?.name?.split(' ')[0] ?? 'there';

  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [data, setData] = useState(null);

  useEffect(() => {
    let isMounted = true;

    async function loadData() {
      try {
        const result = await getProfessorDashboardData();
        if (isMounted) setData(result);
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
        <Sidebar navItems={PROFESSOR_NAV_ITEMS} sectionLabel="Prof" roleBadge="Professor" />
        <div className={styles.contentArea}>
          <p className={styles.loadingText}>Loading dashboard...</p>
        </div>
      </div>
    );
  }

  if (loadError || !data) {
    return (
      <div className={styles.layout}>
        <Sidebar navItems={PROFESSOR_NAV_ITEMS} sectionLabel="Prof" roleBadge="Professor" />
        <div className={styles.contentArea}>
          <p className={styles.loadingText}>Couldn't load the dashboard. Please refresh and try again.</p>
        </div>
      </div>
    );
  }

  const statCards = [
    { id: 'totalStudents', icon: Users, value: data.totalStudents, label: 'Students' },
    { id: 'totalSubjects', icon: BookOpen, value: data.totalSubjects, label: 'Subjects' },
    { id: 'totalModules', icon: FileText, value: data.totalModules, label: 'Total Modules' },
    { id: 'approvedModules', icon: CheckCircle2, value: data.approvedModules, label: 'Approved Modules' },
    { id: 'totalAssessments', icon: ClipboardCheck, value: data.totalAssessments, label: 'Assessments' },
    { id: 'totalPassedAttempts', icon: Award, value: data.totalPassedAttempts, label: 'Passed Attempts' },
  ];

  return (
    <div className={styles.layout}>
      <Sidebar navItems={PROFESSOR_NAV_ITEMS} sectionLabel="Prof" roleBadge="Professor" />

      <div className={styles.contentArea}>
        <header className={styles.topbar}>
          <div className={styles.breadcrumb}>
            <LayoutGrid size={16} />
            Dashboard
          </div>
        </header>

        <main className={styles.main}>
          <h1 className={styles.greeting}>Welcome back, {firstName}! 👋</h1>
          <p className={styles.subtext}>Here's your teaching overview</p>

          <div className={styles.statsGrid}>
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
          </div>
        </main>
      </div>
    </div>
  );
}