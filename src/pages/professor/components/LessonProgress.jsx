import { Check, TriangleAlert } from 'lucide-react';
import styles from '../styles/LessonEditor.module.css';

export default function LessonProgress({ saved, structured, source, generated, failed, outline }) {
  const extraction = !structured ? 'Not required' : !saved ? 'Pending' : source?.extraction || 'Pending';
  const confirmation = !structured ? 'Not required' : extraction !== 'Completed' ? 'Pending' : source?.confirmation || 'Pending';
  const ready = saved && (!structured || confirmation === 'Completed');
  const current = !ready ? 0 : generated && !outline && !source?.recordOnly ? 2 : 1;
  const labels = ['Materials', 'Generate with AI', 'Review & Publish'];
  const targets = [saved && structured && !ready ? 'lesson-sources' : 'lesson-materials', 'lesson-generation', source?.academicReview ? 'lesson-academic-review' : 'lesson-generation'];
  const message = !saved ? 'Save your current lesson materials.' : structured && extraction !== 'Completed' ? 'Extract or refresh the saved curriculum sources.' :
    structured && confirmation !== 'Completed' ? source?.hint || 'Review and confirm the selected sources.' :
    source?.recordOnly ? 'Examination record only. Confirm review-guide coverage to generate.' :
    current === 2 ? (source?.reviewed ? 'Academic review saved. Explicit approval is still required.' : 'Review the generated lesson before publication.') :
    failed ? 'Check the generation error before retrying.' : 'Generate a draft from the saved materials.';
  return <section className={styles.progress} aria-label="Lesson creation progress">
    <ol className={styles.stages}>{labels.map((label,index)=>{
      const attention = index === current && (index === 0 ? [extraction,confirmation].includes('Needs attention') : index === 1 && (failed || outline || source?.recordOnly));
      const status = index < current ? 'Completed' : attention ? 'Needs attention' : index === current ? 'Current' : 'Pending';
      return <li key={label}><button type="button" data-state={status} aria-current={index === current ? 'step' : undefined}
        disabled={index > current || (index === 1 && source?.recordOnly)} onClick={()=>document.getElementById(targets[index])?.focus()}>
        <span aria-hidden="true">{index < current ? <Check size={16}/> : attention ? <TriangleAlert size={16}/> : index+1}</span>
        {label}<small>{status}</small></button></li>;
    })}</ol>
    <dl className={styles.substeps}>{[['1A · Upload & Save Materials',saved?'Completed':'Current'],['1B · Extract Sources',extraction],['1C · Review & Confirm Sources',confirmation]].map(([label,status])=><div key={label}><dt>{label}</dt><dd>{status}</dd></div>)}</dl>
    <p role="status">{message}</p>
  </section>;
}
