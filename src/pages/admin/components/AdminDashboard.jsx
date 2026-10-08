import { useRemoteData } from '../../../hooks/useRemoteData';
import { adminErrorMessage } from '../../../utils/adminErrors';
import { LayoutGrid, Users, GraduationCap, BookOpen, FileText, UserCheck, UserX, Activity } from 'lucide-react';
import PageShell from '../../../components/shared/PageShell';
import { ADMIN_NAV_ITEMS } from '../../../data/navigationData';
import { getAdminDashboardData, getAdminRecentActivity } from '../../../services/admin/adminService';
import styles from '../styles/AdminDashboard.module.css';

// ✅ Wired to the real backend — AdminDashboardController.
// getAdminDashboardData() returns a flat summary, not the old
// { stats, subjectEnrollment, recentActivity } shape. Recent activity now
// comes from the separate getAdminRecentActivity() call.

export default function AdminDashboard() {
  const { data, isLoading: statsLoading, error: statsError, reload: retryStats } = useRemoteData(getAdminDashboardData);
  const { data: activity, isLoading: activityLoading, error: activityError, reload: retryActivity } = useRemoteData(getAdminRecentActivity, []);

  const statCards = [
    { id: 'totalStudents', icon: Users, value: data?.totalStudents, label: 'Total Students' },
    { id: 'totalProfessors', icon: GraduationCap, value: data?.totalProfessors, label: 'Total Professors' },
    { id: 'activeUsers', icon: UserCheck, value: data?.activeUsers, label: 'Active Users' },
    { id: 'inactiveUsers', icon: UserX, value: data?.inactiveUsers, label: 'Inactive Users' },
    { id: 'totalSubjects', icon: BookOpen, value: data?.totalSubjects, label: 'Total Subjects' },
    { id: 'totalModules', icon: FileText, value: data?.totalModules, label: 'Total Modules' },
  ];

  return (
    <PageShell responsive navItems={ADMIN_NAV_ITEMS} sectionLabel="Admin" roleBadge="Admin">

      <>
        <header className={styles.topbar}>
          <div className={styles.breadcrumb}>
            <LayoutGrid size={16} />
            Dashboard
          </div>
        </header>

        <main className={styles.main}>
          <h1 className={styles.greeting}>Admin Overview</h1>
          <p className={styles.subtext}>Here's what's happening across HALO today</p>

          <section aria-label="Statistics" aria-busy={statsLoading}>
          {statsLoading ? <p role="status" className={styles.loadingText}>Loading statistics...</p> : statsError || !data ? (
            <div role="alert">
              <p>{adminErrorMessage(statsError, "Couldn't load statistics. Please try again.")}</p>
              <button type="button" onClick={retryStats}>Retry statistics</button>
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

          </section>

          <section aria-label="Recent Activity" aria-busy={activityLoading}>
          <h2 className={styles.sectionTitle}>
            <Activity size={16} />
            Recent Activity
          </h2>
          {activityLoading ? <p role="status" className={styles.loadingText}>Loading recent activity...</p> : activityError ? (
            <div role="alert">
              <p>{adminErrorMessage(activityError, "Couldn't load recent activity. Please try again.")}</p>
              <button type="button" onClick={retryActivity}>Retry activity</button>
            </div>
          ) : <div className={styles.activityList}>
            {activity.slice(0, 10).map((item) => (
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
          </div>}
          </section>
        </main>
      </>
    </PageShell>
  );
}