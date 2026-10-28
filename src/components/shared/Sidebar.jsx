import AccountAvatar from './AccountAvatar';
import LogoutButton from './LogoutButton';
import { Fragment, useState, useId } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import { LogOut, ChevronDown } from 'lucide-react';
import { useAuth } from '../../context/login/useAuth';
import haloLogo from '../../assets/login/Logo.png';
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
function NavigationGroup({ label, icon: Icon, path, items }) {
  const { pathname } = useLocation();
  return <Group key={pathname} label={label} Icon={Icon} childrenItems={items} active={pathname === path || pathname.startsWith(path + '/')} />;
}
function Group({ label, Icon, childrenItems, active }) {
  const [open, setOpen] = useState(active), id = useId();
  return <div><button type="button" data-navigation-group className={styles.navItem + ' ' + styles.groupButton} aria-expanded={open} aria-controls={id} onClick={() => setOpen(value => !value)}>
    <Icon size={18} />{label}<ChevronDown size={16} className={open ? styles.chevronOpen : styles.chevron} />
  </button><div id={id} hidden={!open} className={styles.submenu}>{childrenItems.map(child => <NavLink key={child.path} to={child.path} className={({ isActive }) => styles.navItem + (isActive ? ' ' + styles.navItemActive : '')}>{child.label}</NavLink>)}</div></div>;
}
export default function Sidebar({ navItems = [], progress, sectionLabel = 'Overview', roleBadge }) {
  const { user } = useAuth();


  return (
    <aside className={`${styles.sidebar} ${roleBadge === 'Student' ? styles.studentSidebar : ''}`}>
      <div className={styles.logo}>
        <img src={haloLogo} alt="HALO" width="2172" height="724" />
      </div>

      {roleBadge && <div className={styles.roleBadge}>{roleBadge === 'Superadmin' ? 'Super Admin' : roleBadge}</div>}

      <div className={styles.section}>
        {!navItems.some(item => item.section) && <p className={styles.sectionLabel}>{sectionLabel}</p>}
        <nav className={styles.nav}>
          {navItems.map(({ label, icon: Icon, path, end, section, children }, index) => (
            <Fragment key={path}>
              {section && section !== navItems[index - 1]?.section && <p className={styles.sectionLabel}>{section}</p>}
            {children ? <NavigationGroup label={label} icon={Icon} path={path} items={children} /> : <NavLink
              key={path}
              to={path}
              end={end}
              className={({ isActive }) =>
                `${styles.navItem} ${isActive ? styles.navItemActive : ''}`
              }
            >
              <Icon size={18} />
              {label}
            </NavLink>}
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
          <div className={styles.avatar}><AccountAvatar initial={user.name?.trim().charAt(0) || '?'} /></div>
          <div>
            <p className={styles.userName}>{user.name}</p>
            <p className={styles.userStatus}>Online</p>
          </div>
          <LogoutButton className={styles.logoutBtn}><LogOut size={16} /></LogoutButton>
        </div>
      )}
    </aside>
  );
}
