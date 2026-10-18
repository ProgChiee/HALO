import styles from '../styles/Quiz.module.css';

export default function QuizVersion({ version, currentVersion, continuing }) {
 if (!Number.isInteger(version) || version < 1) return null;
 return <div className={styles.introMeta} aria-label="Quiz version">
  <p><strong>Quiz Version {version}</strong></p>
  {continuing && Number.isInteger(currentVersion) && currentVersion > version && <p>
   You are continuing an unfinished Version {version} attempt. Its original questions are preserved. New attempts use Version {currentVersion}.
  </p>}
 </div>;
}
