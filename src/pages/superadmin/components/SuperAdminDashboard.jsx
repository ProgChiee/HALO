import { useState, useEffect } from 'react';
import { LayoutGrid, Users, UsersRound, UserCheck, UserX, GraduationCap, Activity } from 'lucide-react';
import PageShell from '../../../components/shared/PageShell';
import { SUPERADMIN_NAV_ITEMS } from '../../../data/navigationData';
import { getSuperAdminDashboardData, getActivityLogs } from '../../../services/superadmin/superadminService';
import styles from '../styles/SuperAdminDashboard.module.css';

// ⚠️ Redesigned for the real backend. SuperAdminDashboardResponse is a
// FLAT object — { totalUsers, totalStudents, totalProfessors, totalAdmins,
// activeUsers, inactiveUsers } — not the old { stats, recentActivity }
// shape (the old code would have crashed destructuring this). Stat cards
// below are built from these fields directly. "Recent Activity" now comes
// from the separate, already-confirmed getActivityLogs() call instead
// (since the dashboard response itself has no activity feed).

export default function SuperAdminDashboard() {
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [data, setData] = useState(null);
  const [activity, setActivity] = useState([]);

  useEffect(() => {
    let isMounted = true;

    async function loadData() {
      try {
        const [dashboard, logs] = await Promise.all([
          getSuperAdminDashboardData(),
          getActivityLogs(),
        ]);
        if (isMounted) {
          setData(dashboard);
          setActivity(logs.slice(0, 10)); // show the 10 most recent
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
      <PageShell navItems={SUPERADMIN_NAV_ITEMS} sectionLabel="Superadmin" roleBadge="Superadmin">
        <p className={styles.loadingText}>Loading dashboard...</p>
      </PageShell>
    );
  }

  if (loadError || !data) {
    return (
      <PageShell navItems={SUPERADMIN_NAV_ITEMS} sectionLabel="Superadmin" roleBadge="Superadmin">
        <p className={styles.loadingText}>Couldn't load the dashboard. Please refresh and try again.</p>
      </PageShell>
    );
  }

  const statCards = [
    { id: 'totalUsers', icon: UsersRound, value: data.totalUsers, label: 'Total Users' },
    { id: 'activeUsers', icon: UserCheck, value: data.activeUsers, label: 'Active Users' },
    { id: 'inactiveUsers', icon: UserX, value: data.inactiveUsers, label: 'Inactive Users' },
    { id: 'totalAdmins', icon: Users, value: data.totalAdmins, label: 'Total Admins' },
    { id: 'totalProfessors', icon: GraduationCap, value: data.totalProfessors, label: 'Total Professors' },
    { id: 'totalStudents', icon: UserCheck, value: data.totalStudents, label: 'Total Students' },
  ];

  return (
    <PageShell navItems={SUPERADMIN_NAV_ITEMS} sectionLabel="Superadmin" roleBadge="Superadmin">
      <header className={styles.topbar}>
        <div className={styles.breadcrumb}>
          <LayoutGrid size={16} />
          Dashboard
        </div>
      </header>

      <main className={styles.main}>
        <h1 className={styles.greeting}>Platform Overview</h1>
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
              <div>
                <p className={styles.activityText}>
                  <span className={styles.activityActor}>{item.userName}</span> {item.action}
                </p>
              </div>
              <span className={styles.activityTime}>{new Date(item.createdAt).toLocaleString()}</span>
            </div>
          ))}
          {activity.length === 0 && (
            <p className={styles.loadingText}>No recent activity yet.</p>
          )}
        </div>
      </main>
    </PageShell>
  );
}