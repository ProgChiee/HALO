import PageShell from '../../../components/shared/PageShell';
import Topbar from '../../../components/shared/Topbar';
import layout from '../../../components/shared/PageLayout.module.css';
import { ADMIN_NAV_ITEMS } from '../../../data/navigationData';

export default function SubjectManagement() {
  return (
    <PageShell responsive navItems={ADMIN_NAV_ITEMS} sectionLabel="Admin" roleBadge="Admin">
      <Topbar title="Subject Management" />
      <main className={layout.regular}>
        <p>Subjects, weekly modules, and lessons are managed by professors.</p>
      </main>
    </PageShell>
  );
}
