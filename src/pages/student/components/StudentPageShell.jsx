import PageShell from '../../../components/shared/PageShell';
import { STUDENT_NAV_ITEMS } from '../../../data/navigationData';
import styles from '../styles/StudentPageShell.module.css';

export default function StudentPageShell({ children }) {
  return <PageShell responsive navItems={STUDENT_NAV_ITEMS} navigationLabel="HALO · Student" className={styles.shell}>
    {children}
  </PageShell>;
}
