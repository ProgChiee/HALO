import { useStudentWorkflow } from '../../../context/student/useStudentWorkflow';
import modeStyles from '../../admin/acting/StudentMode.module.css';
import PageShell from '../../../components/shared/PageShell';
import { STUDENT_NAV_ITEMS } from '../../../data/navigationData';
import styles from '../styles/StudentPageShell.module.css';

export default function StudentPageShell({ children }) {
  const workflow = useStudentWorkflow();
  const items = workflow.acting ? STUDENT_NAV_ITEMS.map(item => ({ ...item, path: item.path.replace('/student', workflow.basePath) })) : STUDENT_NAV_ITEMS;
  return <PageShell responsive roleBadge="Student" navItems={items} navigationLabel="HALO · Student" className={styles.shell}>
    {workflow.acting && <section className={`${modeStyles.banner} ${workflow.preview ? modeStyles.previewBanner : modeStyles.supportBanner}`} aria-label="Student acting mode">
      <strong>{workflow.preview ? 'PREVIEW MODE - Viewing as: ' : 'SUPPORT MODE - REAL STUDENT DATA - Acting as: '}{workflow.target.name || 'Student'}</strong>
      <p>{workflow.preview ? 'No changes will be saved to this Student account.' : 'Actions may modify this Student account and are audited.'}</p>
      <div className={modeStyles.actions}>
        <button type="button" disabled={workflow.leaving} onClick={() => workflow.leave(true)}>Change Account</button>
        <button type="button" disabled={workflow.leaving} onClick={() => workflow.leave(false)}>Exit Mode</button>
      </div>
      {workflow.exitError && <p role="alert">{workflow.exitError}</p>}
    </section>}
    {children}
  </PageShell>;
}
