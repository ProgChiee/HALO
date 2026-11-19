import { useEffect, useRef, useState } from 'react';
import Button from '../../../components/shared/Button';
import LessonMarkdown from '../../../components/shared/LessonMarkdown';
import { professorErrorMessage } from '../../../utils/professorErrors';
import { useProfessorWorkflow } from '../../../context/professor/useProfessorWorkflow';
import styles from '../styles/LessonSourceSelection.module.css';

export default function LessonSourceSelection({ moduleId, revisionVersion, disabled, onChanged }) {
  const { api, preview } = useProfessorWorkflow();
  const [view, setView] = useState(null), [choices, setChoices] = useState([]);
  const [mode, setMode] = useState('SOURCE_GROUNDED'), [lesson, setLesson] = useState(null), [acknowledged, setAcknowledged] = useState(false);
  const [busy, setBusy] = useState(false), [loading, setLoading] = useState(true), [error, setError] = useState('');
  const alive = useRef(false), lock = useRef(false);
  useEffect(() => {
    const controller = new AbortController(); alive.current = true;
    async function load() {
      try {
        const value = await api.getSources(moduleId, revisionVersion, { signal: controller.signal });
        if (!controller.signal.aborted) { setView(value); setChoices(value.source?.choices || []); setMode(value.source?.mode || 'SOURCE_GROUNDED'); setLesson(value.lesson || null); setAcknowledged(false); }
      } catch (e) { if (!controller.signal.aborted) setError(professorErrorMessage(e)); }
      finally { if (!controller.signal.aborted) setLoading(false); }
    }
    if (!preview) load();
    return () => { alive.current = false; controller.abort(); };
  }, [api, moduleId, revisionVersion, preview]);
  if (preview) return null;
  const blocked = disabled || loading || busy || !view;
  const canonical = values => JSON.stringify(values.map(c => ({ fileId: String(c.fileId), instructional: !!c.instructional, sectionIds: [...c.sectionIds].sort() })).sort((a, b) => a.fileId.localeCompare(b.fileId)));
  const unchanged = !!view?.source?.confirmedBy && canonical(choices) === canonical(view.source.choices || []) && mode === (view.source.mode || 'SOURCE_GROUNDED');
  const reviewed = !!view?.source?.review && JSON.stringify(lesson) === JSON.stringify(view.lesson);
  function choose(fileId, sectionId, selected) {
    setChoices(previous => {
      const item = previous.find(c => c.fileId === fileId) || { fileId, sectionIds: [], instructional: false };
      const sectionIds = selected ? [...item.sectionIds, sectionId] : item.sectionIds.filter(id => id !== sectionId);
      return [...previous.filter(c => c.fileId !== fileId), ...(sectionIds.length ? [{ ...item, sectionIds }] : [])];
    });
  }
  async function run(action) {
    if (blocked || lock.current) return;
    lock.current = true; setBusy(true); setError('');
    try {
      if (action === 'extract') await api.extractSources(moduleId, view.version, revisionVersion);
      else if (action === 'review') await api.reviewExpansion(moduleId, view.version, revisionVersion, lesson, acknowledged);
      else await api.confirmSources(moduleId, view.version, revisionVersion, choices, mode);
      if (alive.current) onChanged();
    } catch (e) { if (alive.current) setError(professorErrorMessage(e)); }
    finally { lock.current = false; if (alive.current) setBusy(false); }
  }
  return <section className={styles.panel} aria-label="Chapter and week source selection">
    <h3>Chapter / Week Sources</h3>
    <p>Extract saved PDFs, inspect the exact text and confirm only this module's sections. Week and chapter numbers do not necessarily match.</p>
    <p>Keep curriculum outlines marked as outline-only. OBTL-Guided expansion can elaborate confirmed topics without another PDF; supplementary materials are optional. Source-Grounded lessons require substantive instructional references. Images and scanned PDFs require a readable PDF replacement.</p>
    {!view?.source && !loading && <p>Full-document legacy scope has not been converted. Extraction starts an explicit draft selection; published content stays unchanged.</p>}
    {loading && <p role="status">Loading source selection...</p>}
    {error && <p role="alert">{error}</p>}
    <Button disabled={blocked} variant="secondary" onClick={() => run('extract')}>{busy ? 'Processing sources...' : 'Extract / Refresh Sources'}</Button>
    {view?.source && <>
      <label>Generation mode <select value={mode} disabled={blocked} onChange={e => { setMode(e.target.value); setAcknowledged(false); }}>
        <option value="SOURCE_GROUNDED">Source-Grounded Lesson</option><option value="OBTL_EXPANSION">OBTL-Guided AI Lesson Expansion</option>
      </select></label>
      {mode === 'OBTL_EXPANSION' && <p>AI-generated elaborations use educational knowledge within the confirmed curriculum. They are not text extracted from the OBTL. Review and correct academic accuracy before approval.</p>}
      <p role="status">{unchanged ? 'Sources confirmed. Generate, review and explicitly approve before Students can access the lesson.' : 'Review required. Confirm the current boundaries and generation mode; detection does not approve them.'}</p>
      {view.source.documents.map(doc => {
        const choice = choices.find(c => c.fileId === doc.fileId);
        return <fieldset key={doc.fileId} disabled={blocked} className={styles.document}>
          <legend>{doc.name}</legend>
          <p>Table reading order and repeated headings can be ambiguous. Open every selected preview and verify topics, outcomes and page boundaries. Unchecked sections are excluded.</p>
          <p>Ambiguous table boundaries cannot be confirmed. Review the original PDF and supply a clearly bounded chapter-specific reference if isolation fails. Refresh Sources to re-extract older drafts; published selections stay unchanged.</p>
          {doc.extraction.sections.map(section => <div key={section.id} className={styles.section}>
            <label><input type="checkbox" checked={!!choice?.sectionIds.includes(section.id)} onChange={e => choose(doc.fileId, section.id, e.target.checked)} /> {section.heading}</label>
            <small>Pages {section.spans.map(s => s.page).join(', ')} · Title term matches: {section.titleMatches} (suggestion only)</small>
            <details><summary>Preview exact permitted text and boundary warnings</summary>
              <p>{section.warnings.map(w => w.replaceAll('_', ' ').toLowerCase()).join('; ')}</p>
              {section.spans.map(span => <div key={`${span.page}:${span.start}`}><strong>Page {span.page}, characters {span.start}–{span.end}</strong><pre>{doc.extraction.pages[span.page - 1].text.slice(span.start, span.end)}</pre></div>)}
            </details>
          </div>)}
          <label><input type="checkbox" disabled={!choice} checked={!!choice?.instructional} onChange={e => setChoices(items => items.map(c => c.fileId === doc.fileId ? { ...c, instructional: e.target.checked } : c))} /> Selected sections contain substantive instructional explanations, not only curriculum outlines.</label>
        </fieldset>;
      })}
      <p>By confirming, you approve the displayed boundaries for this module. Generation checks relevance and completeness according to the selected mode. Confirming changes clears any previous generated draft.</p>
      <Button disabled={blocked || !choices.length || unchanged} onClick={() => run('confirm')}>{unchanged ? 'Sources Confirmed' : 'Confirm Selected Sources'}</Button>
      {mode === 'OBTL_EXPANSION' && unchanged && view.source.instructionVerified && lesson && <fieldset disabled={blocked} className={styles.document}>
        <legend>Review AI-generated elaborations</legend>
        <p>The original confirmed curriculum is shown above. Edit the lesson below, check accuracy and scope, then acknowledge your review. This does not publish the lesson.</p>
        {['objectives', 'knowledge', 'examples', 'summary'].map(field => <label className={styles.reviewField} key={field}>{field === 'knowledge' ? 'Explanations and key concepts' : field === 'examples' ? 'Examples and hospitality scenarios' : field}<textarea rows={5} maxLength={30000} value={lesson[field] || ''} onChange={e => { setLesson({ ...lesson, [field]: e.target.value }); setAcknowledged(false); }} /></label>)}
        <details><summary>Formatted lesson preview</summary>
          <p>Edit Markdown in the fields above. Backslashes before Markdown punctuation make it literal; remove intentional formatting escapes in the editor only after reviewing the result. Saving preserves exactly what you entered.</p>
          {['objectives', 'knowledge', 'examples', 'summary'].map(field => <section key={field} aria-label={`${field} preview`}><h4>{field}</h4><LessonMarkdown>{lesson[field]}</LessonMarkdown></section>)}
        </details>
        <label><input type="checkbox" checked={acknowledged} onChange={e => setAcknowledged(e.target.checked)} /> I reviewed these AI-generated elaborations and accept responsibility for academic accuracy and curriculum alignment.</label>
        <Button disabled={blocked || !acknowledged || reviewed} onClick={() => run('review')}>{reviewed ? 'Academic Review Saved' : 'Save Academic Review'}</Button>
        <p role="status">{reviewed ? 'Review saved. Use Approve & Publish or Publish Revision to make this lesson available.' : 'Academic review is required before publication.'}</p>
      </fieldset>}
    </>}
  </section>;
}
