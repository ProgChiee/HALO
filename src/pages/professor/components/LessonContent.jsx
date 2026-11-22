import { useId, useState } from 'react';
import { FileText } from 'lucide-react';
import LessonMarkdown from '../../../components/shared/LessonMarkdown';
import { VideoPreview } from '../../../components/shared/VideoLearning';
import styles from '../styles/LessonEditor.module.css';

const sections = ['Objectives', 'Knowledge', 'Examples', 'Summary'];

export function GeneratedLessonSections({ lesson }) {
  const id = useId();
  const [active, setActive] = useState(0);
  function navigate(event, index) {
    const next = { ArrowLeft: (index + 3) % 4, ArrowRight: (index + 1) % 4, Home: 0, End: 3 }[event.key];
    if (next === undefined) return;
    event.preventDefault();
    setActive(next);
    event.currentTarget.parentElement.children[next].focus();
  }
  return <div className={styles.contentSections}>
    {lesson.generationMode === 'OBTL_EXPANSION' && <p>AI-generated elaborations guided by confirmed OBTL curriculum; not directly extracted PDF facts. Professor academic review is required before publication.</p>}
    <div role="tablist" aria-label="Generated lesson sections" className={styles.generatedTabs}>
      {sections.map((label,index) => <button key={label} type="button" role="tab"
        id={id+'-tab-'+index} aria-controls={id+'-panel-'+index} aria-selected={active === index}
        tabIndex={active === index ? 0 : -1} onClick={() => setActive(index)}
        onKeyDown={event => navigate(event,index)}>{label}</button>)}
    </div>
    {sections.map((label,index) => <div key={label} role="tabpanel" id={id+'-panel-'+index}
      aria-labelledby={id+'-tab-'+index} hidden={index !== active} tabIndex={0} className={styles.generatedPanel}>
      <LessonMarkdown>{lesson['generated'+label]}</LessonMarkdown>
    </div>)}
  </div>;
}

export function PublishedMaterials({ module }) {
  return <section className={styles.panelCard} aria-label="Published lesson materials">
    <div className={styles.sectionHeading}><h2 className={styles.panelTitle}>Lesson Materials</h2><span className={styles.readOnlyLabel}>Read-only</span></div>
    <p className={styles.panelSubtitle}>The current published sources. Revision drafts do not change these materials.</p>
    <details className={styles.contentDisclosure}><summary>Lesson text</summary><p className={styles.aiGeneratedText}>{module.lessonText || 'No lesson text provided.'}</p></details>
    <details className={styles.contentDisclosure}><summary>Notes for the AI Mentor</summary><p className={styles.aiGeneratedText}>{module.aiNotes || 'No additional notes.'}</p></details>
    {module.youtubeLink && <details className={styles.contentDisclosure}><summary>Lesson video</summary><VideoPreview url={module.youtubeLink} /></details>}
    <div className={styles.attachments}><h3>Attachments <span className={styles.readOnlyLabel}>({module.files?.length || 0})</span></h3>
      {module.files?.length ? <ul className={styles.fileList}>{module.files.map(file => <li key={file.id} className={styles.fileListItem}><FileText size={16} aria-hidden="true" /><span className={styles.fileListName}>{file.originalFileName}</span></li>)}</ul> : <p className={styles.panelSubtitle}>No attachments.</p>}
    </div>
  </section>;
}
