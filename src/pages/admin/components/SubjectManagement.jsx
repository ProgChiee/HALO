import PageShell from '../../../components/shared/PageShell';
import { ADMIN_NAV_ITEMS } from '../../../data/navigationData';

export default function SubjectManagement() {
  return (
    <PageShell navItems={ADMIN_NAV_ITEMS} sectionLabel="Admin" roleBadge="Admin">
      <main style={{ padding: '2rem' }}>
        <h1>Subject Management</h1>
        <p>Subjects, weekly modules, and lessons are managed by professors.</p>
      </main>
    </PageShell>
  );
}
