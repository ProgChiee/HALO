import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { LayoutGrid, BookOpen, FileText, Users, GraduationCap } from 'lucide-react';
import Sidebar from '../../../components/shared/Sidebar';
import { useAuth } from '../../../context/login/AuthContext';
import { PROFESSOR_NAV_ITEMS } from '../../../data/navigationData';
import { getProfessorDashboardData } from '../../../services/professor/professorService';
import styles from '../styles/ProfessorDashboard.module.css';

// Maps the string icon names from the mock data to actual icon components
const ICONS = { BookOpen, FileText, Users };

export default function ProfessorDashboard() {
  const { user } = useAuth();
  const [isLoading, setIsLoading] = useState(true);
  const [data, setData] = useState(null);

  useEffect(() => {
    let isMounted = true;

    async function loadData() {
      const result = await getProfessorDashboardData();
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
      <div className={styles.layout}>
        <Sidebar navItems={PROFESSOR_NAV_ITEMS} sectionLabel="Prof" roleBadge="Professor" />
        <div className={styles.contentArea}>
          <p className={styles.loadingText}>Loading dashboard...</p>
        </div>
      </div>
    );
  }

  const { stats, subjects } = data;
  const lastName = user?.name?.split(' ').slice(-1)[0] ?? 'there';

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
          <h1 className={styles.greeting}>Welcome, Dr. {lastName}!</h1>
          <p className={styles.subtext}>Here's an overview of your teaching activity</p>

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
                </div>
              );
            })}
          </div>

          <div className={styles.sectionHeaderRow}>
            <h2 className={styles.sectionTitle}>Enrolled Subjects</h2>
            <Link to="/professor/subjects" className={styles.manageLink}>Manage</Link>
          </div>

          <div className={styles.subjectsList}>
            {subjects.map((subject) => (
              <div key={subject.id} className={styles.subjectRow}>
                <span className={styles.subjectIcon}>
                  <GraduationCap size={16} />
                </span>
                <div className={styles.subjectInfo}>
                  <span className={styles.subjectName}>{subject.name}</span>
                  <span className={styles.subjectMeta}>
                    {subject.modules} modules · {subject.students} students
                  </span>
                </div>
                <span className={styles.subjectStatus}>
                  {subject.status === 'active' ? 'Active' : 'Inactive'}
                </span>
              </div>
            ))}
          </div>
        </main>
      </div>
    </div>
  );
}