import { Search, Eye, LogIn } from 'lucide-react';
import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { Link, Navigate, Outlet, useLocation, useNavigate, useParams } from 'react-router-dom';
import Dialog from '../../../components/shared/Dialog';
import Button from '../../../components/shared/Button';
import PageShell from '../../../components/shared/PageShell';
import ProfessorPageShell from '../../professor/components/ProfessorPageShell';
import { ADMIN_NAV_ITEMS, PROFESSOR_NAV_ITEMS } from '../../../data/navigationData';
import { ProfessorWorkflowContext } from '../../../context/professor/useProfessorWorkflow';
import { useRemoteData } from '../../../hooks/useRemoteData';
import { getProfessorTargets, createProfessorSession, validateProfessorSession, revokeProfessorSession, createActingProfessorScope, createProfessorPreview, validateProfessorPreview, revokeProfessorPreview } from '../../../services/admin/professorModeService';
import styles from './ProfessorMode.module.css';

const previewPath = '/admin/professor-preview';
const selectionPath = '/admin/professor-mode';
function validSession(session, id) {
  return session?.id === id && session?.target?.role === 'PROFESSOR' && session.target.userId
    && Number.isFinite(Date.parse(session.expiresAt)) && Date.parse(session.expiresAt) > Date.now();
}
export function ProfessorModeSelector() {
  const navigate = useNavigate();
  const location = useLocation();
  const [confirmation, setConfirmation] = useState(null);
  const [search, setSearch] = useState('');
  const [selectedId, setSelectedId] = useState(null);
  const [page, setPage] = useState(0);
  const [pending, setPending] = useState(null);
  const [error, setError] = useState('');
  const lock = useRef(false);
  const alive = useRef(false);
  useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
  const loader = useCallback(config => getProfessorTargets(page, search, config), [page, search]);
  const result = useRemoteData(loader);
  async function select(target, preview = false) {
    if (lock.current) return;
    lock.current = true; setSelectedId(target.userId); setPending(target.userId); setError('');
    try {
      const session = await (preview ? createProfessorPreview : createProfessorSession)(target.userId);
      if (!alive.current) { (preview ? revokeProfessorPreview : revokeProfessorSession)(session.id).catch(() => {}); return; }
      if (!validSession(session, session.id) || session.target.userId !== target.userId) throw new Error('Invalid acting session');
      navigate((preview ? previewPath : selectionPath) + '/' + encodeURIComponent(session.id));
    } catch {
      if (alive.current) setError('Could not start Professor Mode. Check that the account is active and try again.');
    } finally {
      lock.current = false;
      if (alive.current) setPending(null);
    }
  }
  return <PageShell responsive navItems={ADMIN_NAV_ITEMS} sectionLabel="Admin" roleBadge="Admin">
    <main className={styles.selector}>
      <h1 className={styles.pageTitle}>Professor Mode</h1><p className={styles.subtitle}>Select an active Professor to preview safely or act on their account with audited changes.</p>
      {location.state?.actingNotice && <p role="status">Professor Mode ended. Select an active account to start a new session.</p>}
      <div className={styles.searchArea}><label htmlFor="professor-mode-search">Search Professors</label>
      <div className={styles.searchField}><Search size={18} aria-hidden="true" />
      <input id="professor-mode-search" placeholder="Search by professor name, email, or ID" value={search} disabled={pending !== null} onChange={e => { setSearch(e.target.value); setPage(0); }} maxLength={100} /></div></div>
      {error && <p role="alert">{error}</p>}
      {result.isLoading ? <p role="status">Loading Professors...</p> : result.error ? <div role="alert">Could not load Professors. <button onClick={result.reload}>Retry</button></div>
        : !result.data?.content?.length ? <p>No active Professors found.</p> : <ul className={styles.targets}>
          {result.data.content.map(target => <li key={target.userId} className={selectedId === target.userId ? styles.selectedTarget : undefined}>
            <div>{selectedId === target.userId && <span className={styles.selectedBadge}><span aria-hidden="true">&#10003;</span> Selected</span>}<strong>{target.name || 'Professor'}</strong><p>{target.email || 'Email not specified'}</p></div>
            <div className={styles.actions}>
              <Button disabled={pending !== null} onClick={() => select(target, true)}><Eye size={16} /> Preview</Button>
              <Button variant="secondary" disabled={pending !== null} onClick={() => { setSelectedId(target.userId); setConfirmation(target); }}><LogIn size={16} /> Act as Professor</Button>
            </div>
          </li>)}
        </ul>}
      <nav aria-label="Professor selection pages" className={styles.pagination}>
        <button disabled={page === 0 || result.isLoading || pending !== null} onClick={() => setPage(p => p - 1)}>Previous</button>
        <span>Page {page + 1}</span>
        <button disabled={result.isLoading || pending !== null || page + 1 >= (result.data?.totalPages ?? 0)} onClick={() => setPage(p => p + 1)}>Next</button>
      </nav>
    </main>
    {confirmation && <Dialog className={styles.confirmDialog} labelledBy="professor-support-title" describedBy="professor-support-description" busy={pending !== null} onClose={() => setConfirmation(null)} initialFocus="button">
      <h2 id="professor-support-title">Act as Professor?</h2>
      <p><strong>{confirmation.name}</strong><br />{confirmation.email}</p>
      <p id="professor-support-description">You are about to perform actions in this Professor's account context. Changes may affect real academic data and will be recorded in the audit log.</p>
      {error && <p role="alert">{error}</p>}
      <div className={styles.actions}><Button variant="secondary" disabled={pending !== null} onClick={() => setConfirmation(null)}>Cancel</Button><Button disabled={pending !== null} onClick={() => select(confirmation)}>Continue as Professor</Button></div>
    </Dialog>}
  </PageShell>;
}
export default function ProfessorModeRoute({ preview = false }) {
  const { actingSessionId } = useParams();
  return <RestoreProfessorMode key={`${preview}-${actingSessionId}`} id={actingSessionId} preview={preview} />;
}
function RestoreProfessorMode({ id, preview }) {
  const loader = useCallback(config => (preview ? validateProfessorPreview : validateProfessorSession)(id, config), [id, preview]);
  const result = useRemoteData(loader);
  if (result.isLoading) return <PageShell responsive navItems={ADMIN_NAV_ITEMS} roleBadge="Admin"><p role="status">Restoring Professor Mode...</p></PageShell>;
  if (result.error) {
    if ([400, 403, 404, 409].includes(result.error.response?.status)) return <Navigate to={selectionPath} replace state={{ actingNotice: true }} />;
    return <PageShell responsive navItems={ADMIN_NAV_ITEMS} roleBadge="Admin"><div className={styles.selector} role="alert">
      Could not validate Professor Mode. <button onClick={result.reload}>Retry</button><Link to="/admin">Back to Admin</Link>
    </div></PageShell>;
  }
  if (!validSession(result.data, id)) return <Navigate to={selectionPath} replace state={{ actingNotice: true }} />;
  return <ActiveProfessorMode session={result.data} preview={preview} />;
}
function ActiveProfessorMode({ session, preview }) {
  const navigate = useNavigate();
  const [leaving, setLeaving] = useState(false);
  const [exitError, setExitError] = useState('');
  const lock = useRef(false);
  const alive = useRef(false);
  const invalid = useCallback(() => navigate(selectionPath, { replace: true, state: { actingNotice: true } }), [navigate]);
  const scope = useMemo(() => createActingProfessorScope(session.id, invalid, preview), [session.id, invalid, preview]);
  useLayoutEffect(() => {
    alive.current = true; scope.resume();
    const timer = setTimeout(invalid, Math.max(0, Date.parse(session.expiresAt) - Date.now()));
    return () => { alive.current = false; scope.dispose(); clearTimeout(timer); };
  }, [scope, session.expiresAt, invalid]);
  async function leave(changeAccount) {
    if (lock.current) return;
    lock.current = true; setLeaving(true); setExitError(''); scope.dispose();
    try {
      await (preview ? revokeProfessorPreview : revokeProfessorSession)(session.id);
      if (alive.current) navigate(changeAccount ? selectionPath : '/admin', { replace: true });
    } catch (error) {
      if (!alive.current) return;
      if ([404, 409].includes(error.response?.status)) navigate(changeAccount ? selectionPath : '/admin', { replace: true });
      else { scope.resume(); setExitError('Could not end the session. Please try Exit Mode again.'); setLeaving(false); }
    } finally { lock.current = false; }
  }
  const value = { acting: true, preview, api: scope.api, target: session.target,
    basePath: (preview ? previewPath : selectionPath) + '/' + session.id, leave, leaving, exitError };
  return <ProfessorWorkflowContext.Provider value={value}>
    {leaving ? <ProfessorPageShell responsive navItems={PROFESSOR_NAV_ITEMS} roleBadge="Professor"><p role="status">Ending Professor Mode...</p></ProfessorPageShell> : <Outlet />}
  </ProfessorWorkflowContext.Provider>;
}
