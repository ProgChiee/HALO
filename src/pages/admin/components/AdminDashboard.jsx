import { useState, useEffect } from 'react';
import { LayoutGrid, Users, GraduationCap, BookOpen, FileText, UserCheck, UserX, Activity } from 'lucide-react';
import Sidebar from '../../../components/shared/Sidebar';
import { ADMIN_NAV_ITEMS } from '../../../data/navigationData';
import { getAdminDashboardData, getAdminRecentActivity } from '../../../services/admin/adminService';
import styles from '../styles/AdminDashboard.module.css';

// ✅ Wired to the real backend — AdminDashboardController.
// getAdminDashboardData() returns a flat summary, not the old
// { stats, subjectEnrollment, recentActivity } shape. Recent activity now
// comes from the separate getAdminRecentActivity() call.

export default function AdminDashboard() {
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [data, setData] = useState(null);
  const [activity, setActivity] = useState([]);

  useEffect(() => {
    let isMounted = true;

    async function loadData() {
      try {
        const [dashboard, recentActivity] = await Promise.all([
          getAdminDashboardData(),
          getAdminRecentActivity(),
        ]);
        if (isMounted) {
          setData(dashboard);
          setActivity(recentActivity.slice(0, 10));
        }
      } catch {
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
        <Sidebar navItems={ADMIN_NAV_ITEMS} />
        <div className={styles.contentArea}>
          <p className={styles.loadingText}>Loading dashboard...</p>
        </div>
      </div>
    );
  }

  if (loadError || !data) {
    return (
      <div className={styles.layout}>
        <Sidebar navItems={ADMIN_NAV_ITEMS} />
        <div className={styles.contentArea}>
          <p className={styles.loadingText}>Couldn't load the dashboard. Please refresh and try again.</p>
        </div>
      </div>
    );
  }

  const statCards = [
    { id: 'totalStudents', icon: Users, value: data.totalStudents, label: 'Total Students' },
    { id: 'totalProfessors', icon: GraduationCap, value: data.totalProfessors, label: 'Total Professors' },
    { id: 'activeUsers', icon: UserCheck, value: data.activeUsers, label: 'Active Users' },
    { id: 'inactiveUsers', icon: UserX, value: data.inactiveUsers, label: 'Inactive Users' },
    { id: 'totalSubjects', icon: BookOpen, value: data.totalSubjects, label: 'Total Subjects' },
    { id: 'totalModules', icon: FileText, value: data.totalModules, label: 'Total Modules' },
  ];

  return (
    <div className={styles.layout}>
      <Sidebar navItems={ADMIN_NAV_ITEMS} />

      <div className={styles.contentArea}>
        <header className={styles.topbar}>
          <div className={styles.breadcrumb}>
            <LayoutGrid size={16} />
            Dashboard
          </div>
        </header>

        <main className={styles.main}>
          <h1 className={styles.greeting}>Admin Overview</h1>
          <p className={styles.subtext}>Here's what's happening across HALO today</p>

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

          <h2 className={styles.sectionTitle}>
            <Activity size={16} />
            Recent Activity
          </h2>
          <div className={styles.activityList}>
            {activity.map((item) => (
              <div key={item.id} className={styles.activityRow}>
                <p className={styles.activityText}>
                  <span className={styles.activityActor}>{item.userName}</span> {item.action}
                </p>
                <span className={styles.activityTime}>{new Date(item.createdAt).toLocaleString()}</span>
              </div>
            ))}
            {activity.length === 0 && (
              <p className={styles.loadingText}>No recent activity yet.</p>
            )}
          </div>
        </main>
      </div>
    </div>
  );
}