import { apiErrorMessage } from '../../../utils/apiErrors';
import { useRemoteData } from '../../../hooks/useRemoteData';
import { LayoutGrid, Users, UsersRound, UserCheck, UserX, GraduationCap, Activity } from 'lucide-react';
import PageShell from '../../../components/shared/PageShell';
import { SUPERADMIN_NAV_ITEMS } from '../../../data/navigationData';
import { getSuperAdminDashboardData, getRecentActivityLogs } from '../../../services/superadmin/superadminService';
import styles from '../styles/SuperAdminDashboard.module.css';

// ⚠️ Redesigned for the real backend. SuperAdminDashboardResponse is a
// FLAT object — { totalUsers, totalStudents, totalProfessors, totalAdmins,
// activeUsers, inactiveUsers } — not the old { stats, recentActivity }
// shape (the old code would have crashed destructuring this). Stat cards
// below are built from these fields directly. "Recent Activity" now comes
// from the separate, already-confirmed getRecentActivityLogs() call instead
// (since the dashboard response itself has no activity feed).

export default function SuperAdminDashboard() {
  const statistics = useRemoteData(getSuperAdminDashboardData);
  const logs = useRemoteData(getRecentActivityLogs, []);
  return <DashboardSections statistics={statistics} logs={logs} />;
}

// Separate section state keeps either successful resource visible independently.
export function DashboardSections({ statistics, logs }) {
  const data = statistics.data;
  const validStatistics = data && !Array.isArray(data) && [
    'totalUsers', 'totalStudents', 'totalProfessors', 'totalAdmins', 'activeUsers', 'inactiveUsers',
  ].every(field => Number.isInteger(data[field]) && data[field] >= 0);
  const validActivity = Array.isArray(logs.data) && logs.data.every(item =>
    item && (typeof item.id === 'number' || typeof item.id === 'string')
    && typeof item.userName === 'string' && typeof item.action === 'string'
    && typeof item.createdAt === 'string' && Number.isFinite(Date.parse(item.createdAt)));
  const activity = validActivity ? logs.data.slice(0, 10) : [];
  const statCards = [
    { id: 'totalUsers', icon: UsersRound, value: data?.totalUsers, label: 'Total Users' },
    { id: 'activeUsers', icon: UserCheck, value: data?.activeUsers, label: 'Active Users' },
    { id: 'inactiveUsers', icon: UserX, value: data?.inactiveUsers, label: 'Inactive Users' },
    { id: 'totalAdmins', icon: Users, value: data?.totalAdmins, label: 'Total Admins' },
    { id: 'totalProfessors', icon: GraduationCap, value: data?.totalProfessors, label: 'Total Professors' },
    { id: 'totalStudents', icon: UserCheck, value: data?.totalStudents, label: 'Total Students' },
  ];

  return (
    <PageShell responsive navItems={SUPERADMIN_NAV_ITEMS} sectionLabel="Superadmin" roleBadge="Superadmin">
      <header className={styles.topbar}>
        <div className={styles.breadcrumb}>
          <LayoutGrid size={16} />
          Dashboard
        </div>
      </header>

      <main className={styles.main}>
        <h1 className={styles.greeting}>Platform Overview</h1>
        <p className={styles.subtext}>Here's what's happening across HALO today</p>

        {statistics.isLoading ? <p role="status">Loading statistics...</p> : statistics.error || !validStatistics ? (
          <div role="alert">
            <p>{apiErrorMessage(statistics.error, 'Unable to load dashboard statistics.')}</p>
            <button onClick={statistics.reload}>Retry statistics</button>
          </div>
        ) : <div className={styles.statsGrid}>
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
        </div>}

        <h2 className={styles.sectionTitle}>
          <Activity size={16} />
          Recent Activity
        </h2>
        {logs.isLoading ? <p role="status">Loading activity...</p> : logs.error || !validActivity ? (
          <div role="alert">
            <p>{apiErrorMessage(logs.error, 'Unable to load recent activity.')}</p>
            <button onClick={logs.reload}>Retry activity logs</button>
          </div>
        ) : <div className={styles.activityList}>
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
        </div>}
      </main>
    </PageShell>
  );
}