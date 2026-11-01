import { useId } from 'react';
import { BookOpen } from 'lucide-react';
import Dialog from '../../../components/shared/Dialog';
import { yearLabels } from '../../../utils/registration';
import styles from '../styles/EnrollmentSubjects.module.css';

export default function EnrollSubjectDialog({ subject, busy, error, onClose, onConfirm }) {
  const titleId = useId(), messageId = useId();
  const metadata = [subject.subjectCode, yearLabels[subject.yearLevel],
    subject.totalWeeks != null ? `${subject.totalWeeks} ${subject.totalWeeks === 1 ? 'week' : 'weeks'}` : null
  ].filter(value => value != null && String(value).trim() !== '');
  return <Dialog labelledBy={titleId} describedBy={messageId} onClose={onClose} closeOnEscape busy={busy} initialFocus="button" className={styles.unenrollDialog}>
    <span className={`${styles.dialogWarning} ${styles.enrollIcon}`} aria-hidden="true"><BookOpen size={22} /></span>
    <h2 id={titleId}>Enroll in Subject?</h2>
    <p className={styles.dialogSubject}>{subject.subjectName}</p>
    {metadata.length > 0 && <ul className={styles.metadata} aria-label="Subject details">{metadata.map((value,index)=><li key={index}>{value}</li>)}</ul>}
    <p id={messageId}>Would you like to enroll in this subject? Once enrolled, it will appear in My Subjects, and you can access its learning activities subject to availability and learning requirements.</p>
    {error && <p role="alert" className={styles.dialogError}>{error === 'PROFILE_INCOMPLETE' ? 'Your academic profile is incomplete. Contact an administrator before enrolling.' : error}</p>}
    <div className={styles.dialogActions}>
      <button type="button" className={styles.secondary} disabled={busy} onClick={onClose}>Cancel</button>
      <button type="button" className={styles.primary} disabled={busy} onClick={onConfirm}>{busy ? 'Enrolling...' : 'Confirm Enrollment'}</button>
    </div>
  </Dialog>;
}
