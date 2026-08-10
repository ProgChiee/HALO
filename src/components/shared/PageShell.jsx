import Sidebar from './Sidebar';
import styles from './PageShell.module.css';

/**
 * Wraps every dashboard-style page (Student, Admin, Professor, Super Admin).
 * Handles the "sidebar stays put, only the content scrolls" layout in ONE
 * place — individual pages no longer need their own .layout/.contentArea
 * CSS for this.
 *
 * Usage:
 * <PageShell navItems={STUDENT_NAV_ITEMS} progress={mockModuleProgress}>
 *   <header>...</header>
 *   <main>...</main>
 * </PageShell>
 */
export default function PageShell({
  navItems,
  progress,
  sectionLabel,
  roleBadge,
  children,
}) {
  return (
    <div className={styles.shell}>
      <Sidebar
        navItems={navItems}
        progress={progress}
        sectionLabel={sectionLabel}
        roleBadge={roleBadge}
      />
      <div className={styles.content}>{children}</div>
    </div>
  );
}