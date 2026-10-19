import { useId, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/login/useAuth';
import Dialog from './Dialog';
import Button from './Button';
import styles from './LogoutButton.module.css';

export default function LogoutButton({ children, className }) {
  const { logout } = useAuth();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const pending = useRef(false);
  const id = useId();
  function close() { if (!pending.current) setOpen(false); }
  async function confirm() {
    if (pending.current) return;
    pending.current = true;
    setBusy(true); setError('');
    try {
      await logout();
      setOpen(false);
      navigate('/login');
    } catch {
      setError('Could not confirm logout with the server. Please check your connection and try again.');
    } finally {
      pending.current = false;
      setBusy(false);
    }
  }
  return <>
    <button type="button" className={className} aria-label="Log out" title="Log out"
      onClick={event => { event.stopPropagation(); event.currentTarget.focus(); setError(''); setOpen(true); }}>{children}</button>
    {open && <Dialog labelledBy={`${id}-title`} describedBy={`${id}-message`} onClose={close}
      busy={busy} initialFocus="button" className={styles.dialog}>
      <h2 id={`${id}-title`}>Log out of HALO?</h2>
      <p id={`${id}-message`}>Are you sure you want to log out of your account?</p>
      {error && <p role="alert">{error}</p>}
      <div className={styles.actions}>
        <Button variant="secondary" onClick={close} disabled={busy}>Cancel</Button>
        <Button variant="danger" onClick={confirm} disabled={busy}>Log Out</Button>
      </div>
    </Dialog>}
  </>;
}
