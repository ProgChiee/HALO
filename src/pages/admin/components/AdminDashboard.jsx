import { useState, useEffect } from 'react';
import { LayoutGrid, Users, GraduationCap, BookOpen, FileText } from 'lucide-react';
import Sidebar from '../../../components/shared/Sidebar';
import { ADMIN_NAV_ITEMS } from '../../../data/navigationData';
import { getAdminDashboardData } from '../../../services/admin/adminService';
import styles from '../styles/AdminDashboard.module.css';

// Maps the string icon names from the mock data to actual icon components
const ICONS = { Users, GraduationCap, BookOpen, FileText };

export default function AdminDashboard() {
  const [isLoading, setIsLoading] = useState(true);
  const [data, setData] = useState(null);

  useEffect(() => {
    let isMounted = true;

    async function loadData() {
      const result = await getAdminDashboardData();
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
        <Sidebar navItems={ADMIN_NAV_ITEMS} sectionLabel="Admin" roleBadge="Admin" />
        <div className={styles.contentArea}>
          <p className={styles.loadingText}>Loading dashboard...</p>
        </div>
      </div>
    );
  }

  const { stats, subjectEnrollment, recentActivity } = data;

  return (
    <div className={styles.layout}>
      <Sidebar navItems={ADMIN_NAV_ITEMS} sectionLabel="Admin" roleBadge="Admin" />

      <div className={styles.contentArea}>
        <header className={styles.topbar}>
          <div className={styles.breadcrumb}>
            <LayoutGrid size={16} />
            ADMIN Dashboard
            <span className={styles.breadcrumbSub}>System Statistics Overview</span>
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
                </div>
              );
            })}
          </div>

          <div className={styles.bottomGrid}>
            <div className={styles.panelCard}>
              <h2 className={styles.panelTitle}>Subject Enrollment</h2>
              <div className={styles.enrollmentList}>
                {subjectEnrollment.map((subject) => (
                  <div key={subject.id} className={styles.enrollmentRow}>
                    <div className={styles.enrollmentHeader}>
                      <span className={styles.enrollmentName}>{subject.name}</span>
                      <span className={styles.enrollmentCount}>{subject.students} students</span>
                    </div>
                    <div className={styles.enrollmentBarTrack}>
                      <div
                        className={styles.enrollmentBarFill}
                        style={{ width: `${subject.percent}%` }}
                      />
                    </div>
                  </div>
                ))}
              </div>
            </div>

            <div className={styles.panelCard}>
              <h2 className={styles.panelTitle}>Recent Activity</h2>
              <div className={styles.activityList}>
                {recentActivity.map((item) => (
                  <div key={item.id} className={styles.activityRow}>
                    <span className={`${styles.activityDot} ${styles[`activityDot_${item.type}`]}`} />
                    <div>
                      <p className={styles.activityText}>{item.text}</p>
                      <p className={styles.activityTime}>{item.time}</p>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </main>
      </div>
    </div>
  );
}