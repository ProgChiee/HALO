import { validateFullName, fullNameErrorMessage } from '../../utils/fullName';
import { Pencil } from 'lucide-react';
import { useContext, useId, useRef, useState } from 'react';
import { useAuth } from '../../context/login/useAuth';
import { ToastContext } from '../../context/notifications/useToast';
import styles from './ProfileName.module.css';

export default function ProfileName({ name, readOnly = false }) {
  const { updateFullName } = useAuth();
  const toast = useContext(ToastContext);
  const [editing, setEditing] = useState(false), [draft, setDraft] = useState('');
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [success, setSuccess] = useState('');
  const locked = useRef(false), editButton = useRef(null);
  const id = useId();
  function close() { setEditing(false); setError(''); queueMicrotask(() => editButton.current?.focus()); }
  async function save(event) {
    event.preventDefault();
    if (locked.current || readOnly) return;
    const { value, error: validationError } = validateFullName(draft);
    if (validationError) { setError(validationError); return; }
    locked.current = true; setBusy(true); setError('');
    try {
      await updateFullName(value);
      close(); setSuccess('Full name updated successfully.');
      toast?.showToast('Full name updated successfully.', 'success');
    } catch (error) {
      setError(fullNameErrorMessage(error));
    } finally { locked.current = false; setBusy(false); }
  }
  return <div className={styles.container}>
    <label className={styles.label} htmlFor={editing ? id : undefined}>Full name</label>
    {editing && !readOnly ? <form onSubmit={save} className={styles.form}>
      <input id={id} className={styles.input} autoFocus autoComplete="name" value={draft} disabled={busy} aria-invalid={Boolean(error)} aria-describedby={error ? id + '-error' : undefined} onChange={event => setDraft(event.target.value)} />
      {error && <p id={id + '-error'} role="alert" className={styles.error}>{error}</p>}
      <div className={styles.actions}><button type="button" className={styles.cancel} disabled={busy} onClick={close}>Cancel</button><button type="submit" className={styles.save} disabled={busy}>{busy ? 'Saving...' : 'Save Changes'}</button></div>
    </form> : <div className={styles.value}><span>{name}</span>{!readOnly && <button ref={editButton} type="button" className={styles.edit} aria-label="Edit full name" onClick={() => { setDraft(name ?? ''); setSuccess(''); setError(''); setEditing(true); }}><Pencil size={16} /></button>}</div>}
    {success && <span role="status" className={styles.success}>{success}</span>}
  </div>;
}
