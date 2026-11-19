import MentorMarkdown from '../../pages/student/components/MentorMarkdown';
import styles from './LessonMarkdown.module.css';

export default function LessonMarkdown({ children }) {
  const text = typeof children === 'string' ? children : '';
  return <div className={styles.content}><MentorMarkdown>{text || 'No content available.'}</MentorMarkdown></div>;
}
