import { useEffect, useRef, useState } from 'react';
import workerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url';

import { locateFragment } from '../../../utils/locateSourceFragment';

export default function ManualSourceReview({ api, moduleId, revisionVersion, doc, section, correction, onChange }) {
  const canvas = useRef(null);
  const [page, setPage] = useState(section.spans[0].page);
  const [boxes, setBoxes] = useState([]);
  const [error, setError] = useState('');
  const [pdf, setPdf] = useState(null);
  const selected = correction?.fragments || [];
  const recovery = section.warnings.includes('MANUAL_ROW_REVIEW_REQUIRED');
  const [label, setLabel] = useState(correction?.displayLabel || section.heading.slice(0,100));
  useEffect(() => {
    const controller = new AbortController();
    let loading;
    async function open() {
      const js = await import('pdfjs-dist');
      js.GlobalWorkerOptions.workerSrc = workerUrl;
      const bytes = await api.getSourceOriginal(moduleId, doc.fileId, revisionVersion, controller.signal);
      const digest = await crypto.subtle.digest('SHA-256', bytes);
      if (Array.from(new Uint8Array(digest), b => b.toString(16).padStart(2, '0')).join('') !== doc.checksum) throw Error();
      if (controller.signal.aborted) return;
      loading = js.getDocument({ data: bytes, isEvalSupported: false, useSystemFonts: true });
      const document = await loading.promise;
      if (!controller.signal.aborted) setPdf({ document, js });
    }
    open().catch(() => { if (!controller.signal.aborted) setError('Could not verify the saved PDF. Refresh Sources and retry.'); });
    return () => { controller.abort(); loading?.destroy(); };
  }, [api, doc.fileId, doc.checksum, moduleId, revisionVersion]);
  useEffect(() => {
    if (!pdf) return;
    let active = true, render;
    async function draw() {
      const original = await pdf.document.getPage(page);
      if (!active) return;
      const viewport = original.getViewport({ scale: 1 });
      const surface = canvas.current;
      const scale = Math.min(2, 1400 / Math.max(viewport.width, viewport.height));
      const view = original.getViewport({ scale });
      surface.width = Math.ceil(view.width); surface.height = Math.ceil(view.height);
      render = original.render({ canvasContext: surface.getContext('2d'), viewport: view });
      await render.promise;
      if (recovery) {
        if (!active) return;
        const rows = doc.extraction.pages[page-1].rows;
        setBoxes(section.spans.filter(s => s.page === page).map(span => {
          const row = rows.find(r => r.start === span.start && r.end === span.end && r.gridKey.startsWith('recovery:'));
          if (!row || original.rotate !== 0) throw Error();
          const factor = scale * original.userUnit;
          return {span, text: section.heading, style: {left: 100*row.left*factor/view.width+'%', top: 100*row.top*factor/view.height+'%',
            width: 100*(row.right-row.left)*factor/view.width+'%', height: 100*(row.bottom-row.top)*factor/view.height+'%'}};
        }));
        return;
      }
      const content = await original.getTextContent();
      if (!active) return;
      const text = doc.extraction.pages[page - 1].text;
      setBoxes(content.items.flatMap(item => {
        if (!item.str) return [];
        const span = locateFragment(text, item.str, page, section.spans);
        const [a,b,c,d,x,y] = pdf.js.Util.transform(view.transform, item.transform);
        if (!span || Math.abs(b) > 0.01 || Math.abs(c) > 0.01 || a <= 0 || d >= 0) return [];
        return [{ span, text: item.str, style: { left: 100*x/view.width+'%', top: 100*(y+d)/view.height+'%', width: 100*item.width*scale/view.width+'%', height: 100*(-d)/view.height+'%' } }];
      }));
    }
    draw().catch(() => { if (active) setError('This PDF page could not be displayed.'); });
    return () => { active = false; render?.cancel(); };
  }, [pdf, page, doc, section, recovery]);
  function toggle(span) {
    const included = selected.some(s => s.page === span.page && s.start === span.start && s.end === span.end);
    const fragments = included ? selected.filter(s => !(s.page === span.page && s.start === span.start && s.end === span.end))
      : [...selected, span].sort((a,b) => a.page-b.page || a.start-b.start);
    onChange({ checksum: doc.checksum, extractionVersion: doc.extraction.version, fragments, ...(recovery && {displayLabel:label}) });
  }
  return <section aria-label="Manual PDF boundary correction">
    {recovery ? <>
      <p>Select each outlined row on its original PDF page and review the assembled excerpt before confirming.</p>
      <label>Section display label <input maxLength={100} value={label} onChange={e => {
        setLabel(e.target.value);
        if(correction) onChange({...correction,displayLabel:e.target.value});
      }} /></label>
    </> : <p>Select text on the original page to keep it in this section. Only uniquely matched text inside its verified boundaries is selectable.</p>}
    <label>PDF page <select value={page} onChange={e => { setBoxes([]); setPage(Number(e.target.value)); }}>
      {[...new Set(section.spans.map(s => s.page))].map(n => <option key={n}>{n}</option>)}
    </select></label>
    {error && <p role="alert">{error}</p>}
    <div style={{position:'relative'}}>
      <canvas ref={canvas} aria-label={'Original PDF page '+page} style={{display:'block',width:'100%',height:'auto'}} />
      {boxes.map(({span,text,style},i) => {
        const checked = selected.some(s => s.page===page && s.start===span.start && s.end===span.end);
        return <button type="button" key={i} aria-pressed={checked} aria-label={text} title={text}
          onClick={() => toggle(span)} style={{...style,position:'absolute',padding:0,border:'1px dotted #267550',background:checked?'rgba(255,190,0,.5)':'transparent'}} />;
      })}
    </div>
    <strong>Exact corrected excerpt</strong>
    <pre>{selected.map(s => doc.extraction.pages[s.page-1].text.slice(s.start,s.end)).join('\n')}</pre>
    <button type="button" onClick={() => onChange(null)}>{recovery ? 'Clear reviewed regions' : 'Use full automatic section'}</button>
  </section>;
}
