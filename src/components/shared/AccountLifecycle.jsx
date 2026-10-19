import { useRef, useState } from 'react';
import { Archive, Power, RotateCcw } from 'lucide-react';
import Dialog from './Dialog';
import { adminErrorMessage } from '../../utils/adminErrors';
import Button from './Button';
import styles from './AccountLifecycle.module.css';

export function AccountStateFilters({ value, onChange }) {
  return <div className={styles.filters} role="group" aria-label="Account state">
    {['ACTIVE', 'INACTIVE', 'ARCHIVED'].map(state => <button key={state} type="button" aria-pressed={value === state} onClick={() => onChange(state)}>{state[0] + state.slice(1).toLowerCase()}</button>)}
  </div>;
}
export default function AccountLifecycle({ account, kind, onConfirm, disabled }) {
  const [action, setAction] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const lock = useRef(false);
  const actions = account.status === 'ARCHIVED' ? ['Restore'] : account.status === 'ACTIVE' ? ['Deactivate', 'Archive'] : account.status === 'INACTIVE' ? ['Reactivate', 'Archive'] : [];
  const message = action === 'Archive'
    ? `This ${kind} will be moved to Archived accounts and will no longer be able to access HALO. ${kind === 'Student' ? 'Learning progress, assessments, AI Mentor history, and academic records' : 'Existing subjects, modules, activities, and historical records'} will remain preserved.`
    : action === 'Deactivate' ? `This ${kind} will temporarily lose access to HALO. ${kind === 'Student' ? 'Their learning records and account information' : 'Their subjects and historical records'} will remain preserved.`
    : action === 'Reactivate' ? 'This account will regain access to HALO.' : 'This account will be restored to account management with its previous activation state.';
  async function confirm() {
    if (lock.current) return;
    lock.current = true; setBusy(true); setError('');
    try { await onConfirm(account, action); setAction(null); }
    catch (failure) { setError(adminErrorMessage(failure)); }
    finally { lock.current = false; setBusy(false); }
  }
  return <div className={styles.actions}>
    {actions.map(label => { const Icon = label === 'Archive' ? Archive : label === 'Restore' ? RotateCcw : Power;
      return <button key={label} type="button" aria-label={label} className={label === 'Deactivate' ? styles.danger : ''} disabled={disabled || busy} onClick={event => { event.currentTarget.focus(); setError(''); setAction(label); }}><Icon size={15} aria-hidden="true" />{label}</button>;
    })}
    {action && <Dialog className={styles.dialog} initialFocus="button" labelledBy={`lifecycle-${kind}-${account.id}`} onClose={() => { if (!lock.current) setAction(null); }} busy={busy}>
      <h2 id={`lifecycle-${kind}-${account.id}`}>{action} {['Restore', 'Reactivate'].includes(action) ? 'Account' : kind}?</h2>
      <p>{account.name}</p><p>{message}</p>
      {error && <p role="alert">{error}</p>}
      <div className={styles.actions}><Button variant="secondary" disabled={busy} onClick={() => setAction(null)}>Cancel</Button><Button variant={action === 'Deactivate' ? 'danger' : action === 'Archive' ? 'secondary' : 'primary'} isLoading={busy} onClick={confirm}>{action === 'Archive' ? `Archive ${kind}` : action}</Button></div>
    </Dialog>}
  </div>;
}
