import { Info } from 'lucide-react';
import styles from '../styles/AcademicProfileNotice.module.css';

export default function AcademicProfileNotice({ onRefresh }) {
  return (
    <section className={styles.notice} aria-labelledby="academic-profile-notice">
      <Info size={20} aria-hidden="true" />
      <div>
        <h2 id="academic-profile-notice">Academic profile incomplete</h2>
        <p>Contact an administrator to verify and complete your Student ID, section and year level. Subjects will be available once your year level is verified. Your existing learning records are preserved.</p>
        {onRefresh && <button type="button" onClick={onRefresh}>Check again</button>}
      </div>
    </section>
  );
}
