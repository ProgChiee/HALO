import { useCallback, useId, useState } from 'react';
import { Eye, History } from 'lucide-react';
import Button from '../../../components/shared/Button';
import Dialog from '../../../components/shared/Dialog';
import { useProfessorWorkflow } from '../../../context/professor/useProfessorWorkflow';
import { useRemoteData } from '../../../hooks/useRemoteData';
import { professorErrorMessage } from '../../../utils/professorErrors';
import styles from '../styles/RevisionHistory.module.css';

const PAGE_SIZE = 5;
const kinds = {
  REVISION: 'Published revision',
  INITIAL_PUBLISHED: 'Initial published baseline',
  LEGACY_LIVE_BASELINE: 'Legacy live baseline',
};
function title(snapshot) {
  return snapshot.snapshotKind === 'REVISION' ? `Revision ${snapshot.revisionNumber}` : (kinds[snapshot.snapshotKind] || 'Published snapshot');
}
function date(value) {
  if (!value || typeof value !== 'string') return 'Not recorded';
  // Backend LocalDateTime fields use HALO's Asia/Manila calendar, without an offset.
  const parsed = new Date(/(?:Z|[+-]\d{2}:\d{2})$/i.test(value) ? value : `${value}+08:00`);
  if (Number.isNaN(parsed.getTime())) return 'Not recorded';
  return new Intl.DateTimeFormat('en-PH', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Manila' }).format(parsed);
}
function account(id) { return id == null ? 'Not recorded' : `Account ID ${id}`; }
function Metadata({ snapshot }) {
  return <>
    <span className={styles.badge}>{snapshot.snapshotKind || 'Unknown snapshot type'}</span>
    {snapshot.snapshotKind === 'LEGACY_LIVE_BASELINE' && <p className={styles.helper}>Captured from the live lesson. This is not a recovered numbered revision; earlier publication details may be unavailable.</p>}
    {snapshot.snapshotKind === 'INITIAL_PUBLISHED' && <p className={styles.helper}>The initial published lesson captured before revisions. This baseline is not a numbered revision.</p>}
    <dl className={styles.metadata}>
      <div><dt>Published</dt><dd>{date(snapshot.publishedAt)}</dd></div>
      <div><dt>Recorded / captured</dt><dd>{date(snapshot.recordedAt)}</dd></div>
      <div><dt>Author</dt><dd>{account(snapshot.revisedBy)}</dd></div>
      <div><dt>Publisher</dt><dd>{account(snapshot.publishedBy)}</dd></div>
      <div><dt>Published module version</dt><dd>{snapshot.publishedModuleVersion ?? 'Not recorded'}</dd></div>
      <div><dt>Last revised</dt><dd>{date(snapshot.revisedAt)}</dd></div>
    </dl>
  </>;
}
function LoadError({ error, retry, label }) {
  return <div className={styles.feedback} role="alert">
    <p>{professorErrorMessage(error, 'Could not load revision history. Please try again.')}</p>
    <Button variant="secondary" onClick={retry}>{label}</Button>
  </div>;
}
function Details({ moduleId, revisionNumber, onClose }) {
  const { api } = useProfessorWorkflow();
  const headingId = useId();
  const descriptionId = useId();
  const load = useCallback(config => api.getRevisionHistoryVersion(moduleId, revisionNumber, config), [api, moduleId, revisionNumber]);
  const { data, isLoading, error, reload } = useRemoteData(load);
  return <Dialog className={styles.dialog} labelledBy={headingId} describedBy={descriptionId} onClose={onClose} initialFocus="button">
    <div className={styles.header}>
      <h2 id={headingId}>{!isLoading && !error && data?.metadata ? title(data.metadata) : 'Revision details'}</h2>
      <Button variant="secondary" onClick={onClose}>Close details</Button>
    </div>
    <p id={descriptionId} className={styles.helper}>Read-only published snapshot. Viewing it does not change the current lesson or draft.</p>
    {isLoading ? <p role="status">Loading revision details...</p> : error ? <LoadError error={error} retry={reload} label="Retry details" /> : data ? <>
      <Metadata snapshot={data.metadata} />
      <p className={styles.helper}>Dates shown in Asia/Manila. Account IDs are preserved audit references; names are not supplied by the history API.</p>
      {[
        ['Lesson content', data.lessonText], ['AI Mentor notes', data.aiNotes], ['YouTube link', data.youtubeLink],
        ['Generated objectives', data.generatedObjectives], ['Generated knowledge', data.generatedKnowledge],
        ['Generated examples', data.generatedExamples], ['Generated summary', data.generatedSummary],
      ].map(([label, content]) => <section className={styles.content} key={label}>
        <h3>{label}</h3><p>{content?.trim() ? content : 'Not recorded'}</p>
      </section>)}
      <section className={styles.content}><h3>Attachment filenames</h3>
        {data.files?.length ? <ul>{data.files.map((file, i) => <li key={file.key || i}>{file.originalFileName || 'Filename not recorded'}</li>)}</ul> : <p>No attachment filenames recorded.</p>}
      </section>
    </> : <p>No snapshot details are available.</p>}
  </Dialog>;
}
function HistoryPage({ moduleId, page, setPage }) {
  const { api } = useProfessorWorkflow();
  const [selected, setSelected] = useState(null);
  const load = useCallback(config => api.getRevisionHistory(moduleId, page, PAGE_SIZE, config), [api, moduleId, page]);
  const { data, isLoading, error, reload } = useRemoteData(load);
  if (isLoading) return <p role="status">Loading revision history...</p>;
  if (error) return <LoadError error={error} retry={reload} label="Retry history" />;
  const rows = data?.content || [];
  return <>
    {!rows.length ? <p className={styles.helper}>No published revision history is available yet. A draft is not a published snapshot; older revisions may not have been retained.</p> : <ul className={styles.list}>
      {rows.map(snapshot => <li className={styles.row} key={snapshot.revisionNumber}>
        <div className={styles.header}><h4>{title(snapshot)}</h4>
          <button type="button" className={styles.action} aria-label={`View details: ${title(snapshot)}`} onClick={() => setSelected(snapshot.revisionNumber)}><Eye size={16} aria-hidden="true" /> View Details</button>
        </div>
        <Metadata snapshot={snapshot} />
      </li>)}
    </ul>}
    {(data?.totalPages > 1 || page > 0) && <nav className={styles.pagination} aria-label="Revision history pages">
      <Button variant="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</Button>
      <span>Page {page + 1} of {Math.max(data?.totalPages || 1, page + 1)}</span>
      <Button variant="secondary" disabled={data?.last !== false || page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Next</Button>
    </nav>}
    {selected !== null && <Details key={selected} moduleId={moduleId} revisionNumber={selected} onClose={() => setSelected(null)} />}
  </>;
}
function HistoryToggle({ moduleId, disabled, embedded }) {
  const [open, setOpen] = useState(false);
  const [page, setPage] = useState(0);
  const regionId = useId();
  if (embedded) return <section className={styles.embedded} aria-label="Revision history">
    <h2>Revision History</h2><p className={styles.helper}>Published snapshots only · Read-only · Dates in Asia/Manila</p>
    <HistoryPage key={page} moduleId={moduleId} page={page} setPage={setPage} />
  </section>;
  return <div className={styles.container}>
    <button type="button" className={styles.action} disabled={disabled} aria-expanded={open} aria-controls={regionId}
      onClick={() => { setOpen(value => !value); setPage(0); }}><History size={18} aria-hidden="true" />{open ? 'Hide Revision History' : 'Revision History'}</button>
    {open && <section id={regionId} className={styles.section} aria-label="Revision history">
      <h3>Revision History</h3><p className={styles.helper}>Published snapshots only · Read-only · Dates in Asia/Manila</p>
      <HistoryPage key={page} moduleId={moduleId} page={page} setPage={setPage} />
    </section>}
  </div>;
}
export default function RevisionHistory({ moduleId, disabled = false, embedded = false }) {
  const { preview } = useProfessorWorkflow();
  // No history route exists in Professor Preview; never fall back to a normal/acting endpoint.
  if (preview) return null;
  return <HistoryToggle key={moduleId} moduleId={moduleId} disabled={disabled} embedded={embedded} />;
}
