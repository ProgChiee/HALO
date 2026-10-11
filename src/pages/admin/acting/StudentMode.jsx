import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { Link, Navigate, Outlet, useLocation, useNavigate, useParams } from 'react-router-dom';
import Button from '../../../components/shared/Button';
import Dialog from '../../../components/shared/Dialog';
import PageShell from '../../../components/shared/PageShell';
import StudentPageShell from '../../student/components/StudentPageShell';
import { ADMIN_NAV_ITEMS, STUDENT_NAV_ITEMS } from '../../../data/navigationData';
import { StudentWorkflowContext } from '../../../context/student/useStudentWorkflow';
import { useRemoteData } from '../../../hooks/useRemoteData';
import { getStudentTargets, createStudentPreview, validateStudentPreview, revokeStudentPreview, createStudentSession, validateStudentSession, revokeStudentSession, createActingStudentScope } from '../../../services/admin/studentModeService';
import styles from './StudentMode.module.css';

const selectionPath = '/admin/student-mode';
function validSession(session, id) {
  return session?.id === id && session?.target?.role === 'STUDENT' && session.target.userId
    && Number.isFinite(Date.parse(session.expiresAt)) && Date.parse(session.expiresAt) > Date.now();
}
export function StudentModeSelector() {
  const navigate = useNavigate();
  const location = useLocation();
  const [selected, setSelected] = useState(null);
  const [confirmSupport, setConfirmSupport] = useState(false);
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [pending, setPending] = useState(null);
  const [error, setError] = useState('');
  const lock = useRef(false);
  const alive = useRef(false);
  useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
  const loader = useCallback(config => getStudentTargets(page, search, config), [page, search]);
  const result = useRemoteData(loader);
  async function select(target, preview) {
    if (lock.current) return;
    lock.current = true; setPending(target.userId); setError('');
    try {
      const session = await (preview ? createStudentPreview : createStudentSession)(target.userId);
      if (!alive.current) { (preview ? revokeStudentPreview : revokeStudentSession)(session.id).catch(() => {}); return; }
      if (!validSession(session, session.id) || session.target.userId !== target.userId) throw new Error('Invalid acting session');
      navigate((preview ? '/admin/student-preview' : selectionPath) + '/' + encodeURIComponent(session.id));
    } catch {
      if (alive.current) setError('Could not start Student Mode. Check that the account is active and try again.');
    } finally {
      lock.current = false;
      if (alive.current) setPending(null);
    }
  }
  return <PageShell responsive navItems={ADMIN_NAV_ITEMS} sectionLabel="Admin" roleBadge="Admin">
    <main className={styles.selector}>
      <h1>Student Mode</h1><p>Select a Student to preview their learning experience safely. Support Mode is available separately for real, audited actions.</p>
      {location.state?.actingNotice && <p role="status">Student Mode ended. Select an active account to start a new session.</p>}
      <label htmlFor="student-mode-search">Search Students</label>
      <input id="student-mode-search" value={search} disabled={pending !== null} onChange={e => { setSearch(e.target.value); setPage(0); }} maxLength={100} />
      {error && <p role="alert">{error}</p>}
      {result.isLoading ? <p role="status">Loading Students...</p> : result.error ? <div role="alert">Could not load Students. <button onClick={result.reload}>Retry</button></div>
        : !result.data?.content?.length ? <p>No active Students found.</p> : <ul className={styles.targets}>
          {result.data.content.map(target => <li key={target.userId}>
            <div><strong>{target.name || 'Student'}</strong><p>{target.email || 'Email not specified'}</p></div>
            <button disabled={pending !== null} onClick={() => { setSelected(target); setError(''); }} aria-label={'Select ' + (target.name || 'Student')}>
              {pending === target.userId ? 'Starting...' : 'Select Student'}
            </button>
          </li>)}
        </ul>}
      {selected && <section className={styles.choices} aria-label="Student mode choices">
        <h2>{selected.name || 'Student'}</h2>
        <div><h3>Student Preview Mode <small>Recommended</small></h3>
          <p>Safely explore the Student experience without changing real Student data.</p>
          <button disabled={pending !== null} onClick={() => select(selected, true)}>Preview as Student</button></div>
        <div><h3>Act on Student Account <small>Restricted</small></h3>
          <p>Perform real actions on behalf of this Student. Actions are audited.</p>
          <button disabled={pending !== null} onClick={() => setConfirmSupport(true)}>Enter Support Mode</button></div>
      </section>}
      {confirmSupport && <Dialog className={styles.confirmDialog} labelledBy="support-title" describedBy="support-description" busy={pending !== null}
        onClose={() => { if (!lock.current) setConfirmSupport(false); }} initialFocus="button">
        <h2 id="support-title">Act on Student Account?</h2>
        <p id="support-description">You are about to enter Support Mode for {selected.name || 'Student'}. Actions may change this Student's real quiz attempts, progress, completion, badges, Mentor history, and other academic records. Actions will be recorded in the audit trail.</p>
        {error && <p role="alert">{error}</p>}
        <div className={styles.actions}><Button variant="secondary" disabled={pending !== null} onClick={() => { if (!lock.current) setConfirmSupport(false); }}>Cancel</Button>
          <Button disabled={pending !== null} onClick={() => select(selected, false)}>{pending !== null ? 'Starting...' : 'Confirm Support Mode'}</Button></div>
      </Dialog>}
      <nav aria-label="Student selection pages" className={styles.actions}>
        <button disabled={page === 0 || result.isLoading || pending !== null} onClick={() => setPage(p => p - 1)}>Previous</button>
        <span>Page {page + 1}</span>
        <button disabled={result.isLoading || pending !== null || page + 1 >= (result.data?.totalPages ?? 0)} onClick={() => setPage(p => p + 1)}>Next</button>
      </nav>
    </main>
  </PageShell>;
}
export default function StudentModeRoute({ preview = false }) {
  const { actingSessionId } = useParams();
  return <RestoreStudentMode key={`${preview}:${actingSessionId}`} id={actingSessionId} preview={preview} />;
}
function RestoreStudentMode({ id, preview }) {
  const loader = useCallback(config => (preview ? validateStudentPreview : validateStudentSession)(id, config), [id, preview]);
  const result = useRemoteData(loader);
  if (result.isLoading) return <PageShell responsive navItems={ADMIN_NAV_ITEMS} roleBadge="Admin"><p role="status">Restoring Student Mode...</p></PageShell>;
  if (result.error) {
    if ([400, 403, 404, 409].includes(result.error.response?.status)) return <Navigate to={selectionPath} replace state={{ actingNotice: true }} />;
    return <PageShell responsive navItems={ADMIN_NAV_ITEMS} roleBadge="Admin"><div className={styles.selector} role="alert">
      Could not validate Student Mode. <button onClick={result.reload}>Retry</button><Link to="/admin">Back to Admin</Link>
    </div></PageShell>;
  }
  if (!validSession(result.data, id)) return <Navigate to={selectionPath} replace state={{ actingNotice: true }} />;
  return <ActiveStudentMode session={result.data} preview={preview} />;
}
function ActiveStudentMode({ session, preview }) {
  const navigate = useNavigate();
  const [leaving, setLeaving] = useState(false);
  const [exitError, setExitError] = useState('');
  const lock = useRef(false);
  const alive = useRef(false);
  const invalid = useCallback(() => navigate(selectionPath, { replace: true, state: { actingNotice: true } }), [navigate]);
  const scope = useMemo(() => createActingStudentScope(session.id, invalid, preview), [session.id, invalid, preview]);
  useLayoutEffect(() => {
    alive.current = true; scope.resume();
    const timer = setTimeout(invalid, Math.max(0, Date.parse(session.expiresAt) - Date.now()));
    return () => { alive.current = false; scope.dispose(); clearTimeout(timer); };
  }, [scope, session.expiresAt, invalid]);
  async function leave(changeAccount) {
    if (lock.current) return;
    lock.current = true; setLeaving(true); setExitError(''); scope.dispose();
    try {
      await (preview ? revokeStudentPreview : revokeStudentSession)(session.id);
      if (alive.current) navigate(changeAccount ? selectionPath : '/admin', { replace: true });
    } catch (error) {
      if (!alive.current) return;
      if ([404, 409].includes(error.response?.status)) navigate(changeAccount ? selectionPath : '/admin', { replace: true });
      else { scope.resume(); setExitError('Could not end the session. Please try Exit Mode again.'); setLeaving(false); }
    } finally { lock.current = false; }
  }
  const value = { acting: true, preview, identity: `${preview ? "preview" : "support"}:${session.id}`, api: scope.api, target: session.target,
    basePath: (preview ? '/admin/student-preview' : selectionPath) + '/' + session.id, leave, leaving, exitError };
  return <StudentWorkflowContext.Provider value={value}>
    {leaving ? <StudentPageShell responsive navItems={STUDENT_NAV_ITEMS} roleBadge="Student"><p role="status">Ending Student Mode...</p></StudentPageShell> : <Outlet />}
  </StudentWorkflowContext.Provider>;
}
