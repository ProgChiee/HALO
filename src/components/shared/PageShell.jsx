import { useState, useEffect, useSyncExternalStore } from 'react';
import { Menu, X } from 'lucide-react';
import Sidebar from './Sidebar';
import Dialog from './Dialog';
import styles from './PageShell.module.css';

const mediaQuery = '(max-width: 1024px)';
const isCompact = () => window.matchMedia?.(mediaQuery).matches ?? false;
const subscribe = callback => {
  const media = window.matchMedia?.(mediaQuery);
  media?.addEventListener('change', callback);
  return () => media?.removeEventListener('change', callback);
};

function ResponsiveShell({ children, ...sidebarProps }) {
  const compact = useSyncExternalStore(subscribe, isCompact, () => false);
  return <ResponsiveLayout compact={compact} sidebarProps={sidebarProps}>{children}</ResponsiveLayout>;
}
function ResponsiveLayout({ compact, sidebarProps, children }) {
  const [open, setOpen] = useState(false);
  useEffect(() => {
    const media = window.matchMedia?.(mediaQuery);
    const closeOnDesktop = event => { if (!event.matches) setOpen(false); };
    media?.addEventListener('change', closeOnDesktop);
    return () => media?.removeEventListener('change', closeOnDesktop);
  }, []);
  return <div className={styles.shell + ' ' + styles.responsive}>
    {!compact && <Sidebar {...sidebarProps} />}
    <div className={styles.content}>
      {compact && <div className={styles.mobileBar}>
        <button type="button" className={styles.menuButton} aria-label="Open navigation" aria-expanded={open}
          aria-controls={open ? 'superadmin-navigation' : undefined} onClick={event => { event.currentTarget.focus(); setOpen(true); }}><Menu size={22} /></button>
        <span>HALO ? Super Admin</span>
      </div>}
      {children}
    </div>
    {open && compact && <Dialog labelledBy="superadmin-navigation-title" onClose={() => setOpen(false)} className={styles.drawer} initialFocus="button">
      <div className={styles.drawerHeader}>
        <h2 id="superadmin-navigation-title">Navigation</h2>
        <button type="button" className={styles.menuButton} aria-label="Close navigation" onClick={() => setOpen(false)}><X size={22} /></button>
      </div>
      <div id="superadmin-navigation" onClick={event => {
        if (event.target.closest('a, button')) setOpen(false);
      }}><Sidebar {...sidebarProps} /></div>
    </Dialog>}
  </div>;
}

export default function PageShell({ navItems, progress, sectionLabel, roleBadge, children, responsive = false }) {
  const sidebarProps = { navItems, progress, sectionLabel, roleBadge };
  if (responsive) return <ResponsiveShell {...sidebarProps}>{children}</ResponsiveShell>;
  return <div className={styles.shell}>
    <Sidebar {...sidebarProps} />
    <div className={styles.content}>{children}</div>
  </div>;
}
