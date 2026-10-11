import { Fragment } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { LogOut } from 'lucide-react';
import { useAuth } from '../../context/login/useAuth';
import haloLogo from '../../assets/login/Icon.png';
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
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate('/login');
  }

  return (
    <aside className={styles.sidebar}>
      <div className={styles.logo}>
        <img src={haloLogo} alt="HALO" width="64" height="64" />
      </div>

      {roleBadge && <div className={styles.roleBadge}>{roleBadge === 'Superadmin' ? 'Super Admin' : roleBadge}</div>}

      <div className={styles.section}>
        {!navItems.some(item => item.section) && <p className={styles.sectionLabel}>{sectionLabel}</p>}
        <nav className={styles.nav}>
          {navItems.map(({ label, icon: Icon, path, end, section }, index) => (
            <Fragment key={path}>
              {section && section !== navItems[index - 1]?.section && <p className={styles.sectionLabel}>{section}</p>}
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
            </Fragment>
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
          <button
            type="button"
            className={styles.logoutBtn}
            onClick={handleLogout}
            aria-label="Log out"
            title="Log out"
          >
            <LogOut size={16} />
          </button>
        </div>
      )}
    </aside>
  );
}