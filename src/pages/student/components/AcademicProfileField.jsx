import { useContext, useId, useRef, useState } from 'react';
import { Pencil } from 'lucide-react';
import { ToastContext } from '../../../context/notifications/useToast';
import { academicYearLevels, yearLabels, registrationHints, validateAcademicField } from '../../../utils/registration';
import { updateAcademicProfile } from '../../../services/student/studentService';
import styles from '../../../components/shared/ProfileName.module.css';

export default function AcademicProfileField({ field, label, value, readOnly, onSaved }) {
  const [editing, setEditing] = useState(false), [draft, setDraft] = useState('');
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [success, setSuccess] = useState('');
  const locked = useRef(false), opener = useRef(null), id = useId();
  const toast = useContext(ToastContext);
  function close() { setEditing(false); setError(''); queueMicrotask(() => opener.current?.focus()); }
  async function save(event) {
    event.preventDefault();
    if (locked.current || readOnly) return;
    const checked = validateAcademicField(field, draft);
    if (checked.error) { setError(checked.error); return; }
    locked.current = true; setBusy(true); setError('');
    try {
      const updated = await updateAcademicProfile(field, checked.value);
      onSaved(updated[field]); close();
      const message = `${label} updated successfully.`;
      setSuccess(message); toast?.showToast(message, 'success');
    } catch (failure) {
      const status = failure?.response?.status, code = failure?.response?.data?.code;
      setError(code === 'STUDENT_ID_CONFLICT' ? 'This Student ID is already registered.'
        : code === 'PROFILE_INCOMPLETE' ? 'Your academic profile needs administrator recovery before it can be edited.'
        : status === 400 ? registrationHints[field]
        : status === 401 ? 'Your session has expired. Please sign in again.'
        : status === 403 ? 'You are not allowed to update this profile.'
        : status >= 500 ? 'HALO could not save your changes. Please try again.'
        : failure?.response ? 'Your changes could not be saved. Please refresh and try again.'
        : 'Could not reach HALO. Check your connection and try again.');
    } finally { locked.current = false; setBusy(false); }
  }
  const control = { id, className: styles.input, autoFocus: true, value: draft, disabled: busy,
    'aria-invalid': Boolean(error), 'aria-describedby': `${id}-hint${error ? ` ${id}-error` : ''}`, onChange: e => setDraft(e.target.value) };
  return <div className={styles.container}>
    <label className={styles.label} htmlFor={editing ? id : undefined}>{label}</label>
    {editing && !readOnly ? <form onSubmit={save} className={styles.form}>
      {field === 'yearLevel' ? <select {...control}>
        <option value="">Select year level</option>
        {academicYearLevels.map(year => <option key={year} value={year}>{yearLabels[year]}</option>)}
      </select> : <input {...control} type="text" autoComplete="off" />}
      <small id={`${id}-hint`}>{registrationHints[field]}{field === 'yearLevel' && ' This affects which subjects are available to you.'}</small>
      {error && <p id={`${id}-error`} role="alert" className={styles.error}>{error}</p>}
      <div className={styles.actions}><button type="button" className={styles.cancel} disabled={busy} onClick={close}>Cancel</button><button type="submit" className={styles.save} disabled={busy}>{busy ? 'Saving...' : 'Save Changes'}</button></div>
    </form> : <div className={styles.value}><span>{(field === 'yearLevel' ? yearLabels[value] : value?.trim()) || 'Not set'}</span>
      {!readOnly && <button ref={opener} type="button" className={styles.edit} aria-label={`Edit ${label}`} onClick={() => { setDraft(value ?? ''); setError(''); setSuccess(''); setEditing(true); }}><Pencil size={16} /></button>}
    </div>}
    {success && <span role="status" className={styles.success}>{success}</span>}
  </div>;
}
