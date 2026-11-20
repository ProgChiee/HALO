import { useEffect, useRef, useState } from 'react';
import Button from '../../../components/shared/Button';
import ManualSourceReview from './ManualSourceReview';
import LessonMarkdown from '../../../components/shared/LessonMarkdown';
import { professorErrorMessage } from '../../../utils/professorErrors';
import { useProfessorWorkflow } from '../../../context/professor/useProfessorWorkflow';
import styles from '../styles/LessonSourceSelection.module.css';

const boundaryBlocked = section => !section.spans.length || section.warnings.some(w => ['AMBIGUOUS_TABLE_BOUNDARY', 'TABLE_CONTINUATION_UNCONFIRMED'].includes(w));
function chapterTitle(doc, section) {
  const span = section.spans[0];
  if (!span || !/^Chapter \d+/i.test(section.heading) || !section.warnings.includes('MANUAL_ROW_REVIEW_REQUIRED')) return section.heading;
  const excerpt = doc.extraction.pages[span.page-1].text.slice(span.start,span.end).trimStart();
  // Use only the heading block before the first numbered topic in the original column.
  const title = /^(Chapter\s+\d+[^\n]*(?:\n(?!\s*\d+[.)]|\s*\n)[^\n]+){0,3})\n\s*\d+[.)]/i.exec(excerpt)?.[1];
  return title ? title.replace(/\s+/g,' ').trim() : section.heading;
}
const sectionKind = section => section.warnings.includes('EXAMINATION_SECTION') ? 'Examination' : section.warnings.includes('ORIENTATION_SECTION') ? 'Orientation' : section.warnings.includes('CURRICULUM_ACTIVITY_SECTION') ? 'Curriculum activity' : 'Curriculum section';

function SourceExcerpt({ pages, spans }) {
  const [exact, setExact] = useState(false);
  return <details className={styles.excerpt}>
    <summary>Permitted source text</summary>
    {spans.length === 0 ? <p>No verified text spans for this section.</p> : <>
      <label className={styles.viewSwitch}>Text presentation
        <select value={exact ? 'exact' : 'readable'} onChange={event => setExact(event.target.value === 'exact')}>
          <option value="readable">Readable View</option>
          <option value="exact">Exact Source View</option>
        </select>
      </label>
      <small>{exact ? 'Original extracted spacing and line breaks.' : 'Reflowed for reading. Use Exact Source View to inspect the original spacing.'}</small>
      {spans.map(span => <div key={`${span.page}:${span.start}`}>
        <strong>Page {span.page}, characters {span.start}–{span.end}</strong>
        <pre className={exact ? undefined : styles.readable}>{pages[span.page - 1].text.slice(span.start, span.end)}</pre>
      </div>)}
    </>}
  </details>;
}

export default function LessonSourceSelection({ moduleId, revisionVersion, disabled, onChanged }) {
  const { api, preview } = useProfessorWorkflow();
  const [view, setView] = useState(null), [choices, setChoices] = useState([]);
  const [editing, setEditing] = useState(null);
  const [query, setQuery] = useState(''), [filter, setFilter] = useState('All Sections');
  const reviewPanel = useRef(null);
  const [suggestions, setSuggestions] = useState({});
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
  const canonical = values => JSON.stringify(values.map(c => ({ fileId: String(c.fileId), instructional: !!c.instructional, sectionIds: [...c.sectionIds].sort(), correction: c.correction || null })).sort((a, b) => a.fileId.localeCompare(b.fileId)));
  const unchanged = !!view?.source?.confirmedBy && canonical(choices) === canonical(view.source.choices || []) && mode === (view.source.mode || 'SOURCE_GROUNDED');
  const reviewed = !!view?.source?.review && JSON.stringify(lesson) === JSON.stringify(view.lesson);
  const recoveryIncomplete = choices.some(choice => {
    const doc = view?.source?.documents.find(d => d.fileId === choice.fileId);
    const row = doc?.extraction.sections.find(s => choice.sectionIds.includes(s.id) && s.warnings.includes('MANUAL_ROW_REVIEW_REQUIRED'));
    return row && (choice.sectionIds.length !== 1 || !choice.correction?.displayLabel?.trim() ||
      row.spans.length !== choice.correction.fragments.length);
  });
  function choose(fileId, sectionId, selected) {
    setChoices(previous => {
      const item = previous.find(c => c.fileId === fileId) || { fileId, sectionIds: [], instructional: false };
      const sectionIds = selected ? [...item.sectionIds, sectionId] : item.sectionIds.filter(id => id !== sectionId);
      const next = {...item, sectionIds};
      delete next.correction;
      return [...previous.filter(c => c.fileId !== fileId), ...(sectionIds.length ? [next] : [])];
    });
  }
  async function run(action, fileId) {
    if (blocked || lock.current) return;
    lock.current = true; setBusy(true); setError('');
    try {
      if (action === 'recognize') {
        const result = await api.recognizeSources(moduleId, fileId, view.source.id, revisionVersion);
        if (alive.current) setSuggestions(previous => ({...previous,[view.source.id+'/'+fileId]:result}));
        return;
      }
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
    <details className={styles.help}><summary>Source selection guidance</summary><p>Keep curriculum plans marked as outline-only. OBTL-Guided expansion accepts confirmed outlines without supplementary PDFs. Source-Grounded lessons require substantive instructional references. Use readable PDFs and check table boundaries against the original pages.</p></details>
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
      <div className={styles.tools}>
        <label>Search sections<input type="search" placeholder="Chapter, week or title" value={query} onChange={e => setQuery(e.target.value)}/></label>
        <label>Show<select value={filter} onChange={e => setFilter(e.target.value)}>{['All Sections','Chapters','Weeks','Examinations','Needs Review'].map(item => <option key={item}>{item}</option>)}</select></label>
      </div>
      {view.source.documents.map(doc => {
        const choice = choices.find(c => c.fileId === doc.fileId);
        const hints = suggestions[view.source.id+'/'+doc.fileId];
        const visible = doc.extraction.sections.filter(section => {
          const title = chapterTitle(doc, section);
          return title.toLowerCase().includes(query.trim().toLowerCase()) &&
            (filter === 'All Sections' || (filter === 'Needs Review' && boundaryBlocked(section)) ||
             (filter === 'Examinations' && sectionKind(section) === 'Examination') ||
             (filter === 'Chapters' && /\bchapters?\b/i.test(title)) || (filter === 'Weeks' && /\bweeks?\b/i.test(title)));
        });
        const groups = [
          ['Selectable Chapters / Weeks', visible.filter(s => !boundaryBlocked(s) && sectionKind(s) !== 'Examination')],
          ['Examinations and Assessments', visible.filter(s => !boundaryBlocked(s) && sectionKind(s) === 'Examination')],
          ['Needs Boundary Review', visible.filter(boundaryBlocked)]
        ];
        return <fieldset key={doc.fileId} disabled={blocked} className={styles.document}>
          <legend>{doc.name}</legend>
          {view.recognitionEnabled && <div>
            <Button variant="secondary" disabled={blocked || !!hints} onClick={() => run('recognize',doc.fileId)}>Suggest structure with Gemini</Button>
            {hints && <div role="status">{hints.length ? <ul>{hints.map((h,i) =>
              <li key={i}>{h.heading} · PDF page {h.pages.join(', ')} · {h.sectionId ? 'Review the extracted section below' : 'Boundary unresolved; inspect the original page'}</li>)}</ul> : 'No evidence-backed suggestions.'}</div>}
          </div>}
          {!visible.length && <p role="status">No matching sections in this PDF.</p>}
          {groups.filter(([,items]) => items.length).map(([group,items]) => <section key={group} className={styles.group}><h4>{group} <small>({items.length})</small></h4>{items.map(section => {
            const first = section.spans[0];
            const region = first && doc.extraction.pages[first.page-1]?.rows?.find(r => r.start === first.start && r.end === first.end);
            const schedule = region?.gridKey?.startsWith('recovery:merged|') && region.gridKey.split('|')[1];
            const recoverable = section.id.startsWith('ambiguous-p') ? doc.extraction.sections.filter(s =>
              !boundaryBlocked(s) && s.warnings.includes('MANUAL_ROW_REVIEW_REQUIRED') && s.spans.some(span => section.id === 'ambiguous-p'+span.page)) : [];
            return <div key={section.id} className={styles.section}>
            <label><input type="checkbox" disabled={boundaryBlocked(section) && !choice?.sectionIds.includes(section.id)} checked={!!choice?.sectionIds.includes(section.id)} onChange={e => choose(doc.fileId, section.id, e.target.checked)} /> <span className={styles.sectionTitle}>{chapterTitle(doc,section)}</span></label>
            <small>{sectionKind(section)} · {section.spans.length ? `Pages ${[...new Set(section.spans.map(s => s.page))].join(', ')}` : 'Page boundaries unavailable'} · Title term matches: {section.titleMatches} (suggestion only)</small>
            <details open={choice?.sectionIds.includes(section.id) || undefined}><summary>{boundaryBlocked(section) ? recoverable.length ? 'Review verified rows (' + recoverable.length + ')' : 'Review boundary warning' : 'Preview and review source'}</summary>
            {region?.gridKey?.startsWith('recovery:merged|') && /^Chapter /i.test(section.heading) &&
              <small>{section.heading.endsWith('(opening excerpt)') || boundaryBlocked(section) ? 'Partial chapter — ending unresolved' : 'Complete chapter boundaries for the selected topic and outcome columns'}</small>}
            {schedule && <p>Shared schedule: Weeks {schedule}. Review the highlighted chapter topics and outcomes.</p>}

            {!boundaryBlocked(section) && section.warnings.includes('MANUAL_ROW_REVIEW_REQUIRED') && <p>Recover this row as a new section. Select it alone for this file and review its highlighted PDF regions.</p>}
            {recoverable.length > 0 ? <label className={styles.rowField}>Select a recovered row
              <select value={choice?.sectionIds.length === 1 && recoverable.some(row => row.id === choice.sectionIds[0]) ? choice.sectionIds[0] : ''} onChange={e => {
                const selected = e.target.value;
                if (!selected) return;
                setChoices(items => [...items.filter(c => c.fileId !== doc.fileId),
                  {fileId:doc.fileId,sectionIds:[selected],instructional:false}]);
                setQuery(''); setFilter('All Sections'); setEditing(doc.fileId);
                requestAnimationFrame(() => reviewPanel.current?.focus());
              }}>
                <option value="">{choice ? 'Replace this file’s draft selection with a row…' : 'Choose a row…'}</option>
                {recoverable.map(s => <option key={s.id} value={s.id}>{chapterTitle(doc,s)}</option>)}
              </select></label> : boundaryBlocked(section) && <p>Cannot confirm this section: its source boundaries are unresolved. Refresh Sources or provide a clearly bounded reference. Other safely isolated sections remain selectable.</p>}
              <p>{section.warnings.map(w => w.replaceAll('_', ' ').toLowerCase()).join('; ')}</p>
              {section.warnings.includes('VERIFY_COLUMN_ASSOCIATIONS_AGAINST_ORIGINAL') && <p>Original row wording is preserved. Check that topics, knowledge, outcomes and tasks agree; the extractor does not correct curriculum inconsistencies.</p>}
              <SourceExcerpt pages={doc.extraction.pages} spans={section.spans} />
            {choice?.sectionIds.length === 1 && choice.sectionIds[0] === section.id && section.id !== 'whole' && !boundaryBlocked(section) &&
            <div key={section.id} ref={editing === doc.fileId ? reviewPanel : null} tabIndex={-1} aria-label="Review selected PDF boundaries">
              <button type="button" onClick={() => setEditing(editing === doc.fileId ? null : doc.fileId)}>Review and correct PDF boundaries</button>
              {(editing === doc.fileId || choice.correction || section.warnings.includes('MANUAL_ROW_REVIEW_REQUIRED')) && <ManualSourceReview key={section.id} {...{api,moduleId,revisionVersion,doc,section}} correction={choice.correction}
                onChange={correction => setChoices(items => items.map(c => c.fileId === doc.fileId ? {...c,correction} : c))} />}
            </div>}
            </details>
          </div>;})}</section>)}
          <label><input type="checkbox" disabled={!choice} checked={!!choice?.instructional} onChange={e => setChoices(items => items.map(c => c.fileId === doc.fileId ? { ...c, instructional: e.target.checked } : c))} /> Selected sections contain substantive instructional explanations, not only curriculum outlines.</label>
        </fieldset>;
      })}
      <p>By confirming, you approve the displayed boundaries for this module. Generation checks relevance and completeness according to the selected mode. Confirming changes clears any previous generated draft.</p>
      <Button disabled={blocked || !choices.length || unchanged || recoveryIncomplete || choices.some(c => c.correction && (c.sectionIds.length !== 1 || !c.correction.fragments.length))} onClick={() => run('confirm')}>{unchanged ? 'Sources Confirmed' : 'Confirm Selected Sources'}</Button>
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
