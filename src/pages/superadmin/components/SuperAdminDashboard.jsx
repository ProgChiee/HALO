import { useState, useEffect } from 'react';
import { LayoutGrid, Users, UsersRound, UserCheck, UserX, GraduationCap, Layers, Activity } from 'lucide-react';
import PageShell from '../../../components/shared/PageShell';
import { SUPERADMIN_NAV_ITEMS } from '../../../data/navigationData';
import { getSuperAdminDashboardData } from '../../../services/superadmin/superadminService';
import styles from '../styles/SuperAdminDashboard.module.css';

// Maps the string icon names from the mock data to actual icon components
const ICONS = { Users, UsersRound, UserCheck, UserX, GraduationCap, Layers };

export default function SuperAdminDashboard() {
  const [isLoading, setIsLoading] = useState(true);
  const [data, setData] = useState(null);

  useEffect(() => {
    let isMounted = true;

    async function loadData() {
      const result = await getSuperAdminDashboardData();
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
      <PageShell navItems={SUPERADMIN_NAV_ITEMS}>
        <p className={styles.loadingText}>Loading dashboard...</p>
      </PageShell>
    );
  }

  const { stats, recentActivity } = data;

  return (
    <PageShell navItems={SUPERADMIN_NAV_ITEMS}>
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

        <h2 className={styles.sectionTitle}>
          <Activity size={16} />
          Recent Activity
        </h2>
        <div className={styles.activityList}>
          {recentActivity.map((item) => (
            <div key={item.id} className={styles.activityRow}>
              <div>
                <p className={styles.activityText}>
                  <span className={styles.activityActor}>{item.actor}</span> {item.action}
                </p>
              </div>
              <span className={styles.activityTime}>{item.time}</span>
            </div>
          ))}
        </div>
      </main>
    </PageShell>
  );
}