import { useEffect, useRef, useState } from 'react';
import Dialog from '../../../components/shared/Dialog';
import Button from '../../../components/shared/Button';
import { VideoPreview } from '../../../components/shared/VideoLearning';
import { youtubeVideoId } from '../../../utils/youtubeVideo';
import { professorErrorMessage } from '../../../utils/professorErrors';
import { useProfessorWorkflow } from '../../../context/professor/useProfessorWorkflow';
import styles from '../styles/LessonEditor.module.css';

export default function LessonRevision({ moduleId, onPublished }) {
 const { api, preview } = useProfessorWorkflow();
 const [revision, setRevision] = useState(null), [draft, setDraft] = useState(null), [files, setFiles] = useState([]);
 const [busy, setBusy] = useState(false), [loading, setLoading] = useState(true), [error, setError] = useState(''), [confirm, setConfirm] = useState(null), [reload, setReload] = useState(0);
 const alive = useRef(false), lock = useRef(false), upload = useRef(null);
 function accept(value) { setRevision(value || null); setDraft(value ? { lessonText: value.lessonText || '', youtubeLink: value.youtubeLink || '', aiNotes: value.aiNotes || '', keepFiles: value.files.map(f => f.key) } : null); setFiles([]); if (upload.current) upload.current.value = ''; }
 useEffect(() => {
  const controller = new AbortController(); alive.current = true;
  if (!preview) api.getRevision(moduleId, { signal: controller.signal }).then(value => { if (!controller.signal.aborted) accept(value); }).catch(e => { if (!controller.signal.aborted) setError(professorErrorMessage(e)); }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
  return () => { alive.current = false; controller.abort(); };
 }, [api, moduleId, preview, reload]);
 if (preview) return null;
 const changed = revision && draft && (draft.lessonText !== (revision.lessonText || '') || draft.youtubeLink !== (revision.youtubeLink || '') || draft.aiNotes !== (revision.aiNotes || '') || draft.keepFiles.length !== revision.files.length || files.length > 0);
 async function run(action) {
  if (lock.current) return;
  lock.current = true; setBusy(true); setError('');
  try {
   if (action === 'start') { const value = await api.startRevision(moduleId); if (alive.current) accept(value); }
   if (action === 'save') {
    if (draft.youtubeLink.trim() && !youtubeVideoId(draft.youtubeLink)) throw { response: { data: { code: 'INVALID_YOUTUBE_URL' } } };
    const { keepFiles, ...materials } = draft;
    const value = await api.saveRevision(moduleId, { version: revision.version, materials, keepFiles }, files); if (alive.current) accept(value);
   }
   if (action === 'review') { const value = await api.reviewRevision(moduleId, revision.version); if (alive.current) accept(value); }
   if (action === 'discard') { await api.discardRevision(moduleId, revision.version); if (alive.current) accept(null); }
   if (action === 'publish') { await api.publishRevision(moduleId, revision.version); if (alive.current) { accept(null); onPublished(); } }
   if (alive.current) setConfirm(null);
  } catch (e) { if (alive.current) setError(professorErrorMessage(e)); }
  finally { lock.current = false; if (alive.current) setBusy(false); }
 }
 function pick(event) {
  const incoming = Array.from(event.target.files || []);
  if (incoming.some(f => !['application/pdf', 'image/png', 'image/jpeg'].includes(f.type) || !f.size || f.size > 3 * 1024 * 1024)) { setError('Choose readable PDF, PNG or JPG/JPEG files up to 3 MB each.'); event.target.value = ''; return; }
  if (draft.keepFiles.length + incoming.length > 10) { setError('A revision can contain at most 10 files.'); event.target.value = ''; return; }
  setFiles(incoming); setError('');
 }
 const disabled = busy || loading;
 return <section className={styles.panelCard} aria-label="Published lesson revision">
  <h2 className={styles.panelTitle}>{revision ? `Draft revision ${revision.revisionNumber}` : 'Published version'}</h2>
  <p className={styles.panelSubtitle}>{revision ? 'Draft revision - Students are still seeing the current published version.' : 'Create a separate revision to update these published materials.'}</p>
  {loading && <p role="status">Loading revision...</p>}
  {error && <p role="alert">{error}</p>}
  {!revision && !loading && <Button disabled={busy} onClick={() => setConfirm('start')}>Revise Materials</Button>}
  {error && <Button variant="secondary" disabled={busy} onClick={() => { setError(''); setLoading(true); setReload(n => n + 1); }}>Reload revision</Button>}
  {revision && draft && <>
   <div className={styles.field}><label className={styles.label} htmlFor="revision-text">Revision lesson text</label><textarea id="revision-text" className={styles.textarea} rows={6} maxLength={100000} value={draft.lessonText} disabled={disabled} onChange={e => setDraft({ ...draft, lessonText: e.target.value })} /></div>
   <div className={styles.field}><label className={styles.label} htmlFor="revision-youtube">Revision YouTube link</label><input id="revision-youtube" className={styles.input} maxLength={255} value={draft.youtubeLink} disabled={disabled} onChange={e => setDraft({ ...draft, youtubeLink: e.target.value })} /><VideoPreview url={draft.youtubeLink} /></div>
   <div className={styles.field}><label className={styles.label} htmlFor="revision-notes">Revision notes for the AI Mentor</label><textarea id="revision-notes" className={styles.textarea} rows={3} maxLength={10000} value={draft.aiNotes} disabled={disabled} onChange={e => setDraft({ ...draft, aiNotes: e.target.value })} /></div>
   <ul>{revision.files.filter(f => draft.keepFiles.includes(f.key)).map(f => <li key={f.key} className={styles.revisionFile}><span>{f.originalFileName}</span><Button variant="secondary" disabled={disabled} onClick={() => setDraft({ ...draft, keepFiles: draft.keepFiles.filter(key => key !== f.key) })}>Remove from revision</Button></li>)}</ul>
   <div className={styles.field}><label className={styles.label} htmlFor="revision-files">Add revision files (PDF, PNG, JPG/JPEG; 3 MB each)</label><input id="revision-files" ref={upload} type="file" multiple accept=".pdf,.png,.jpg,.jpeg" disabled={disabled} onChange={pick} /></div>
   <div className={styles.revisionActions}>
    <Button disabled={disabled || !changed} onClick={() => run('save')}>Save Draft</Button>
    <Button variant="secondary" disabled={disabled} onClick={() => setConfirm('discard')}>Discard Revision</Button>
    <Button variant="secondary" disabled={disabled || changed} onClick={() => run('review')}>Review Changes</Button>
    <Button disabled={disabled || changed || revision.state !== 'REVIEWED'} onClick={() => setConfirm('publish')}>Publish Revision</Button>
   </div>
   {busy && <p role="status">Processing revision...</p>}
   {changed && <p>Save Draft before reviewing or publishing.</p>}
   {revision.state === 'REVIEWED' && <section aria-label="Reviewed revision"><h3>Review generated lesson</h3>{[['Objectives', revision.generatedObjectives], ['Knowledge', revision.generatedKnowledge], ['Examples', revision.generatedExamples], ['Summary', revision.generatedSummary]].map(([title, text]) => <div key={title}><h4>{title}</h4><p className={styles.revisionText}>{text}</p></div>)}</section>}
  </>}
  {confirm && <Dialog className={styles.confirmDialog} labelledBy="revision-confirm-title" describedBy="revision-confirm-message" busy={busy} onClose={() => setConfirm(null)} initialFocus="button">
   <h2 id="revision-confirm-title">{confirm === 'start' ? 'Revise Published Lesson?' : confirm === 'discard' ? 'Discard Revision?' : 'Publish Revision?'}</h2>
   <p id="revision-confirm-message">{confirm === 'start' ? 'You are creating a new revision of this published lesson. Students will continue seeing the current published version until the revision is reviewed and published.' : confirm === 'discard' ? 'The draft changes will be discarded. The published lesson will remain unchanged.' : 'Replace the published materials with this reviewed revision? Student history and existing assessments will remain unchanged.'}</p>
   {error && <p role="alert">{error}</p>}
   <div className={styles.revisionActions}><Button variant="secondary" disabled={busy} onClick={() => setConfirm(null)}>Cancel</Button><Button disabled={busy} onClick={() => run(confirm)}>{busy ? 'Processing...' : confirm === 'start' ? 'Create Revision' : confirm === 'discard' ? 'Discard Revision' : 'Publish Revision'}</Button></div>
  </Dialog>}
 </section>;
}
