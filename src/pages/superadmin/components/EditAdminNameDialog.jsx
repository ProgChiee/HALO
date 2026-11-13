import { useEffect, useRef, useState } from 'react';
import Dialog from '../../../components/shared/Dialog';
import Button from '../../../components/shared/Button';
import { validateFullName, fullNameErrorMessage } from '../../../utils/fullName';
import { updateAdminProfile } from '../../../services/superadmin/superadminService';
import { useToast } from '../../../context/notifications/useToast';
import styles from '../styles/Admins.module.css';

export default function EditAdminNameDialog({ admin, onSaved, onClose }) {
  const [name, setName] = useState(admin.name ?? '');
  const [email, setEmail] = useState(admin.email ?? '');
  const [busy, setBusy] = useState(false), [error, setError] = useState('');
  const lock = useRef(false), alive = useRef(false);
  const { showToast } = useToast();
  useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
  function close() { if (!lock.current) onClose(); }
  async function save(event) {
    event.preventDefault();
    if (lock.current) return;
    const validated = validateFullName(name);
    if (validated.error) { setError(validated.error); return; }
    const normalizedEmail = email.trim().toLowerCase();
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalizedEmail) || normalizedEmail.length > 254) { setError('Please enter a valid email address.'); return; }
    lock.current = true; setBusy(true); setError('');
    try {
      const updated = await updateAdminProfile(admin.id ?? admin.userId, validated.value, normalizedEmail);
      if (!alive.current) return;
      onSaved(updated);
      showToast('Admin updated successfully.', 'success');
      onClose();
    } catch (failure) {
      if (alive.current) setError(failure.response?.data?.code === 'EMAIL_ALREADY_EXISTS' ? 'This email address is already registered.' : failure.response?.data?.code === 'ADMIN_NOT_FOUND'
        ? 'This Admin account is no longer available. Refresh the list.' : failure.response?.data?.code === 'VALIDATION_FAILED' ? 'Please enter a valid full name and email address.' : fullNameErrorMessage(failure).replaceAll('full name', 'Admin profile'));
    } finally { lock.current = false; if (alive.current) setBusy(false); }
  }
  return <Dialog labelledBy="edit-admin-name-title" onClose={close} busy={busy} closeOnEscape className={styles.modal}>
    <h3 id="edit-admin-name-title" className={styles.modalTitle}>Edit Admin</h3>
    <form onSubmit={save} className={styles.form}>
      <div className={styles.field}>
        <label htmlFor="edit-admin-full-name" className={styles.label}>Full name</label>
        <input id="edit-admin-full-name" className={styles.input} value={name} disabled={busy}
          autoComplete="off" aria-invalid={Boolean(error)} aria-describedby={error ? 'edit-admin-name-error' : undefined}
          onChange={event => setName(event.target.value)} />
      </div>
      <div className={styles.field}>
        <label htmlFor="edit-admin-email" className={styles.label}>Email Address</label>
        <input id="edit-admin-email" type="email" required maxLength={254} className={styles.input}
          value={email} disabled={busy} autoComplete="off" onChange={event => setEmail(event.target.value)}
          aria-invalid={Boolean(error)} aria-describedby={error ? 'edit-admin-name-error' : undefined} />
      </div>
      <p className={styles.label}>Changing the email signs this Admin out and invalidates existing password-reset codes.</p>
      {error && <p id="edit-admin-name-error" role="alert" className={styles.formError}>{error}</p>}
      <div className={styles.confirmActions}>
        <Button type="button" variant="secondary" disabled={busy} onClick={close}>Cancel</Button>
        <Button type="submit" isLoading={busy} disabled={busy}>Save Changes</Button>
      </div>
    </form>
  </Dialog>;
}
