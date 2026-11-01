import { useEffect, useId, useRef, useState } from 'react';
import { AlertTriangle } from 'lucide-react';
import Dialog from '../../../components/shared/Dialog';
import { unenrollFromSubject } from '../../../services/student/studentService';
import styles from '../styles/EnrollmentSubjects.module.css';

export default function UnenrollSubjectDialog({ subject, onClose, onSuccess }) {
  const titleId = useId(), messageId = useId();
  const pending = useRef(false), mounted = useRef(true);
  const [busy, setBusy] = useState(false), [error, setError] = useState('');
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  function close() { if (!pending.current) onClose(); }
  async function confirm() {
    if (pending.current) return;
    pending.current = true; setBusy(true); setError('');
    try {
      await unenrollFromSubject(subject.subjectId);
      if (mounted.current) onSuccess(subject);
    } catch (failure) {
      if (mounted.current) setError(unenrollError(failure));
    } finally {
      pending.current = false;
      if (mounted.current) setBusy(false);
    }
  }
  return <Dialog labelledBy={titleId} describedBy={messageId} onClose={close} busy={busy} initialFocus="button" className={styles.unenrollDialog}>
    <span className={styles.dialogWarning} aria-hidden="true"><AlertTriangle size={22} /></span>
    <h2 id={titleId}>Unenroll from Subject?</h2>
    <p className={styles.dialogSubject}>{subject.subjectName || 'Subject unavailable'}</p>
    <p id={messageId}>You will lose access to this subject's learning activities. Your existing progress, assessment results, and learning history will be preserved.</p>
    {error && <p role="alert" className={styles.dialogError}>{error}</p>}
    <div className={styles.dialogActions}>
      <button type="button" className={styles.secondary} onClick={close} disabled={busy}>Keep Subject</button>
      <button type="button" className={styles.dangerOutline} onClick={confirm} disabled={busy}>{busy ? 'Unenrolling...' : 'Confirm Unenroll'}</button>
    </div>
  </Dialog>;
}

function unenrollError(error) {
  const status = error?.response?.status;
  if (status === 401) return 'Your session has expired. Please sign in again.';
  if (status === 403) return 'You are not authorized to unenroll from this subject.';
  if (status === 404) return 'This enrollment is no longer available. Close this dialog and refresh My Subjects.';
  return error?.response ? 'HALO could not complete unenrollment. Please try again.'
    : 'Could not reach HALO. Check your connection and try again. Retrying will not delete your learning history.';
}
