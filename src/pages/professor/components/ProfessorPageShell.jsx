import PageShell from '../../../components/shared/PageShell';
import { useProfessorWorkflow } from '../../../context/professor/useProfessorWorkflow';
import styles from '../../admin/acting/ProfessorMode.module.css';
export default function ProfessorPageShell({ navItems, children, ...props }) {
  const workflow = useProfessorWorkflow();
  const items = workflow.acting ? navItems.map(item => ({ ...item, path: item.path.replace('/professor', workflow.basePath) })) : navItems;
  return <PageShell {...props} navItems={items}>
    {workflow.acting && <section className={styles.banner} aria-label="Professor acting mode">
      <strong>Professor Mode — Acting as {workflow.target.name || 'Professor'}</strong>
      <div className={styles.actions}>
        <button type="button" disabled={workflow.leaving} onClick={() => workflow.leave(true)}>Change Account</button>
        <button type="button" disabled={workflow.leaving} onClick={() => workflow.leave(false)}>Exit Mode</button>
      </div>
      {workflow.exitError && <p role="alert">{workflow.exitError}</p>}
    </section>}
    {children}
  </PageShell>;
}
