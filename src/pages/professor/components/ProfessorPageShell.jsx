import PageShell from '../../../components/shared/PageShell';
import { useProfessorWorkflow } from '../../../context/professor/useProfessorWorkflow';
import styles from '../../admin/acting/ProfessorMode.module.css';
export default function ProfessorPageShell({ navItems, children, ...props }) {
  const workflow = useProfessorWorkflow();
  const items = workflow.acting ? navItems.filter(item => !item.path.endsWith('/settings')).map(item => ({ ...item, path: item.path.replace('/professor', workflow.basePath) })) : navItems;
  return <PageShell {...props} navItems={items}>
    {workflow.acting && <section className={styles.banner} aria-label={workflow.preview ? "Professor preview mode" : "Professor acting mode"}>
      <strong>{workflow.preview ? "Professor Preview — Viewing as " : "Act as Professor — Acting as "}{workflow.target.name || "Professor"}</strong>
      <span>{workflow.preview ? "Read-only" : "Changes affect real academic data and are audited."}</span>
      <div className={styles.actions}>
        <button type="button" disabled={workflow.leaving} onClick={() => workflow.leave(true)}>Change Account</button>
        <button type="button" disabled={workflow.leaving} onClick={() => workflow.leave(false)}>{workflow.preview ? "Exit Preview" : "Exit Mode"}</button>
      </div>
      {workflow.exitError && <p role="alert">{workflow.exitError}</p>}
    </section>}
    {children}
  </PageShell>;
}
