import { NavLink } from 'react-router-dom';
import { useAuth } from '../../context/login/AuthContext';
import styles from './Sidebar.module.css';

/**
 * Generic sidebar shell — reusable across roles.
 * Usage:
 * <Sidebar
 *   navItems={[{ label: 'Dashboard', icon: LayoutGrid, path: '/student' }, ...]}
 *   progress={{ label: 'Module 1 progress', percent: 37, detail: '3 of 8 lessons done' }}
 *   sectionLabel="Admin"   // optional, defaults to "Overview"
 *   roleBadge="Admin"      // optional pill shown below the logo
 * />
 */
export default function Sidebar({ navItems = [], progress, sectionLabel = 'Overview', roleBadge }) {
  const { user } = useAuth();

  return (
    <aside className={styles.sidebar}>
      <div className={styles.logo}>
        HALO
      </div>

      {roleBadge && <div className={styles.roleBadge}>{roleBadge}</div>}

      <div className={styles.section}>
        <p className={styles.sectionLabel}>{sectionLabel}</p>
        <nav className={styles.nav}>
          {navItems.map(({ label, icon: Icon, path, end }) => (
            <NavLink
              key={path}
              to={path}
              end={end}
              className={({ isActive }) =>
                `${styles.navItem} ${isActive ? styles.navItemActive : ''}`
              }
            >
              <Icon size={18} />
              {label}
            </NavLink>
          ))}
        </nav>
      </div>

      {progress && (
        <div className={styles.progressCard}>
          <p className={styles.progressLabel}>{progress.label}</p>
          <div className={styles.progressBarTrack}>
            <div
              className={styles.progressBarFill}
              style={{ width: `${progress.percent}%` }}
            />
          </div>
          <p className={styles.progressDetail}>
            {progress.percent}% · {progress.detail}
          </p>
        </div>
      )}

      {user && (
        <div className={styles.userFooter}>
          <div className={styles.avatar}>{user.name?.charAt(0) ?? '?'}</div>
          <div>
            <p className={styles.userName}>{user.name}</p>
            <p className={styles.userStatus}>Online</p>
          </div>
        </div>
      )}
    </aside>
  );
}