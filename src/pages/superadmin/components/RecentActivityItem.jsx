import { Activity, LogIn, ShieldCheck, Users, TriangleAlert } from 'lucide-react';
import styles from '../styles/SuperAdminDashboard.module.css';
const icons = { mode: ShieldCheck, auth: LogIn, account: Users, warning: TriangleAlert };
export default function RecentActivityItem({ item }) {
  const Icon = icons[item.activityKind] || Activity;
  const date = new Date(item.createdAt);
  const validDate = Number.isFinite(date.getTime());
  return <li className={styles.activityRow}>
    <span className={styles.activityIcon} aria-hidden="true"><Icon size={17} /></span>
    <div className={styles.activityContent}>
      <p className={styles.activityText}>{item.activityTitle || 'Recorded activity'}</p>
      <p className={styles.activityDescription}>{item.activityDescription || `${item.userName || 'Administrator'} recorded an administrative activity. See the audit log for details.`}</p>
    </div>
    <time className={styles.activityTime} dateTime={validDate ? item.createdAt : undefined}>
      {validDate ? date.toLocaleString(undefined, { year: 'numeric', month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' }) : 'Time unavailable'}
    </time>
  </li>;
}
