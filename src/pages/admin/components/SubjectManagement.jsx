import PageShell from '../../../components/shared/PageShell';
import { ADMIN_NAV_ITEMS } from '../../../data/navigationData';

export default function SubjectManagement() {
  return (
    <PageShell responsive navItems={ADMIN_NAV_ITEMS} sectionLabel="Admin" roleBadge="Admin">
      <main style={{ padding: 'var(--space-8)' }}>
        <h1>Subject Management</h1>
        <p>Subjects, weekly modules, and lessons are managed by professors.</p>
      </main>
    </PageShell>
  );
}
