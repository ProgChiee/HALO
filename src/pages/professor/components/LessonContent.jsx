import { FileText } from 'lucide-react';
import LessonMarkdown from '../../../components/shared/LessonMarkdown';
import { VideoPreview } from '../../../components/shared/VideoLearning';
import styles from '../styles/LessonEditor.module.css';

export function GeneratedLessonSections({ lesson }) {
  return <div className={styles.contentSections}>
    {lesson.generationMode === 'OBTL_EXPANSION' && <p>AI-generated elaborations guided by confirmed OBTL curriculum; not directly extracted PDF facts. Professor academic review is required before publication.</p>}
    {['Objectives', 'Knowledge', 'Examples', 'Summary'].map(label => <details key={label} className={styles.contentDisclosure}>
      <summary>{label}</summary>
      <div className={styles.aiGeneratedText}><LessonMarkdown>{lesson[`generated${label}`]}</LessonMarkdown></div>
    </details>)}
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
