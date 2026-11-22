import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';
import { professorErrorMessage } from '../src/utils/professorErrors.js';
import { locateFragment } from '../src/utils/locateSourceFragment.js';

test('PDF highlights require a unique exact match within the selected page and section', () => {
  const text = 'Intro Alpha Exam Alpha';
  const owners = [{ page: 2, start: 0, end: 11 }];
  assert.deepEqual(locateFragment(text, 'Intro', 2, owners), { page: 2, start: 0, end: 5 });
  for (const value of ['Alpha', 'Exam', 'Alpha Exam', 'intro', ' '])
    assert.equal(locateFragment(text, value, 2, owners), null);
  assert.equal(locateFragment(text, 'Intro', 1, owners), null);
});
test('generation warnings distinguish expansion from legacy Source-Grounded drafts', () => {
  const message = code => professorErrorMessage({ response: { status: 422, data: { code } } });
  assert.match(message('EXPANSION_GENERATION_REQUIRED'), /Supplementary PDFs are optional/);
  assert.match(message('EXPANSION_GENERATION_REQUIRED'), /before academic review and publication/);
  assert.match(message('INSTRUCTIONAL_SOURCES_REQUIRED'), /Source-Grounded mode requires instructional references/);
  assert.match(message('INSTRUCTIONAL_SOURCES_REQUIRED'), /explicitly confirm OBTL-Guided/);
});
let dom, server, createRoot, Component, Context;
before(async () => {
  dom = new JSDOM('<html><body></body></html>', { url: 'http://localhost' });
  for (const key of ['window', 'document', 'HTMLElement', 'Event', 'localStorage', 'sessionStorage']) globalThis[key] = dom.window[key];
  globalThis.IS_REACT_ACT_ENVIRONMENT = true;
  ({ createRoot } = await import('react-dom/client'));
  server = await createServer({ server: { middlewareMode: true, hmr:false }, appType: 'custom', ssr:{noExternal:['pdfjs-dist']},
    plugins:[{name:'isolated-pdf-canvas',enforce:'pre',resolveId(id){if(id==='pdfjs-dist')return '\0fixture-pdf';},
      load(id){if(id==='\0fixture-pdf')return 'export const GlobalWorkerOptions={}; export const getDocument=(...args)=>globalThis.fixturePdf(...args);';}}] });
  Component = (await server.ssrLoadModule('/src/pages/professor/components/LessonSourceSelection.jsx')).default;
  ({ ProfessorWorkflowContext: Context } = await server.ssrLoadModule('/src/context/professor/useProfessorWorkflow.js'));
});
after(async () => { await server.close(); dom.window.close(); });
const source = { confirmedBy: null, documents: [{ fileId: 3, name: 'OBTL.pdf', extraction: { pages: [{ number: 1, text: 'Chapter 1\nOutline\nChapter 2\nExcluded' }], sections: [
  { id: 'one', heading: 'Chapter 1', spans: [{ page: 1, start: 0, end: 18 }], titleMatches: 1, warnings: ['PROFESSOR_BOUNDARY_REVIEW_REQUIRED'] },
  { id: 'two', heading: 'Chapter 2', spans: [{ page: 1, start: 18, end: 36 }], titleMatches: 0, warnings: [] },
] } }], choices: [] };
async function mount(api, props = {}, preview = false) {
  const host = document.createElement('div'); document.body.append(host); const root = createRoot(host); let changed = 0;
  await act(async () => root.render(React.createElement(Context.Provider, { value: { api, preview } }, React.createElement(Component, { moduleId: 7, onChanged: () => changed++, ...props }))));
  return { host, changed: () => changed, close: async () => { await act(async () => root.unmount()); host.remove(); } };
}
const button = (ui, text) => [...ui.host.querySelectorAll('button')].find(b => b.textContent === text);
test('structure recognition is opt-in and a review pointer cannot confirm or select sources', async () => {
  const fixture={...source,id:'saved-snapshot'};
  let enabled=false;
  const calls=[];
  const api={getSources:async()=>({version:4,source:fixture,recognitionEnabled:enabled}),
    recognizeSources:async(...args)=>{calls.push(args);return [{sectionId:null,pages:[4],heading:'Week 2'}];}};
  const off=await mount(api);
  try {assert.equal(button(off,'Suggest structure with Gemini'),undefined);} finally {await off.close();}
  enabled=true;
  const ui=await mount(api);
  try {
    assert.equal(calls.length,0);
    await act(async()=>button(ui,'Suggest structure with Gemini').click());
    assert.deepEqual(calls,[[7,3,'saved-snapshot',undefined]]);
    assert.match(ui.host.textContent,/Week 2 · PDF page 4 · Boundary unresolved/);
    assert.equal(button(ui,'Suggest structure with Gemini').disabled,true);
    assert.equal(button(ui,'Confirm Selected Sources').disabled,true);
  } finally {await ui.close();}
});
test('building a manual section requires reviewing the verified PDF region', async () => {
  const fixture = structuredClone(source);
  fixture.documents[0].extraction.sections[0].warnings.push('MANUAL_ROW_REVIEW_REQUIRED');
  const ui = await mount({ getSources: async () => ({version:4, source:fixture}) });
  try {
    await act(async () => ui.host.querySelector('input[type=checkbox]').click());
    assert.match(ui.host.textContent,/Section display label/);
    assert.equal(button(ui,'Confirm Selected Sources').disabled,true);
    assert.equal(ui.host.querySelector('canvas').getAttribute('aria-label'),'Original PDF page 1');
  } finally { await ui.close(); }
});
test('reviewed geometry submits its exact row and keeps the Professor label separate', async () => {
  const fixture=structuredClone(source), doc=fixture.documents[0], section=doc.extraction.sections[0];
  const bytes=new Uint8Array([1,2,3]).buffer;
  doc.checksum=Buffer.from(await crypto.subtle.digest('SHA-256',bytes)).toString('hex');
  doc.extraction.version='fixture-v6';
  section.warnings.push('MANUAL_ROW_REVIEW_REQUIRED');
  doc.extraction.pages[0].rows=[{start:0,end:18,left:10,top:20,right:110,bottom:60,gridKey:'recovery:fixture'}];
  const canvas=dom.window.HTMLCanvasElement.prototype;
  const original=canvas.getContext;
  canvas.getContext=()=>({});
  globalThis.fixturePdf=()=>({destroy(){},promise:Promise.resolve({getPage:async()=>({
    userUnit:1,rotate:0,getViewport:({scale})=>({width:200*scale,height:100*scale}),
    render:()=>({promise:Promise.resolve(),cancel(){}})})})});
  const calls=[];
  const ui=await mount({getSources:async()=>({version:4,source:fixture}),getSourceOriginal:async()=>bytes,
    confirmSources:async(...args)=>calls.push(args)});
  try {
    await act(async()=>ui.host.querySelector('input[type=checkbox]').click());
    for(let i=0;i<30 && !ui.host.querySelector('button[aria-pressed]');i++)
      await act(async()=>{await new Promise(resolve=>setTimeout(resolve,15));});
    const region=ui.host.querySelector('button[aria-pressed]');
    assert.ok(region);assert.equal(region.style.left,'5%');assert.equal(region.style.height,'40%');
    await act(async()=>region.click());
    assert.equal(ui.host.querySelector('section[aria-label="Manual PDF boundary correction"] pre').textContent,'Chapter 1\nOutline\n');
    await act(async()=>button(ui,'Confirm Selected Sources').click());
    assert.deepEqual(calls[0][3][0].correction,{checksum:doc.checksum,extractionVersion:'fixture-v6',fragments:section.spans,displayLabel:'Chapter 1'});
  } finally { await ui.close();canvas.getContext=original;delete globalThis.fixturePdf; }
});
test('exams are labeled and unresolved boundaries cannot be selected alongside safe outline sections', async () => {
  const fixture = structuredClone(source);
  fixture.documents[0].extraction.sections[1].warnings = ['EXAMINATION_SECTION'];
  fixture.documents[0].extraction.sections.push({ id: 'uncertain', heading: 'Unisolated row', spans: [], warnings: ['AMBIGUOUS_TABLE_BOUNDARY'], titleMatches: 0 });
  const calls = [];
  const ui = await mount({ getSources: async () => ({ version: 4, source: fixture }), confirmSources: async (...args) => calls.push(args) });
  try {
    const boxes = ui.host.querySelectorAll('input[type=checkbox]');
    assert.equal(boxes[0].disabled, false); assert.equal(boxes[2].disabled, true);
    assert.match(ui.host.textContent, /Examination/); assert.match(ui.host.textContent, /Other safely isolated sections remain selectable/);
    await act(async () => { const select = ui.host.querySelector('select'); select.value = 'OBTL_EXPANSION'; select.dispatchEvent(new Event('change', { bubbles: true })); });
    await act(async () => boxes[0].click());
    await act(async () => button(ui, 'Confirm Selected Sources').click());
    assert.deepEqual(calls, [[7, 4, undefined, [{ fileId: 3, sectionIds: ['one'], instructional: false }], 'OBTL_EXPANSION']]);
  } finally { await ui.close(); }
});
test('old extraction feedback requests explicit refresh without changing published lessons', () => {
  const message = professorErrorMessage({ response: { data: { code: 'SOURCE_REEXTRACTION_REQUIRED' } } });
  assert.match(message, /extraction version changed/); assert.match(message, /Published lessons stay unchanged/);
});
test('ambiguous boundary rejection explains safe review without confirming', async () => {
  const ui = await mount({ getSources: async () => ({ version: 2, source }), confirmSources: async () => {
    throw { response: { status: 422, data: { code: 'SOURCE_BOUNDARY_REVIEW_REQUIRED' } } };
  } });
  try {
    await act(async () => ui.host.querySelector('input[type=checkbox]').click());
    await act(async () => button(ui, 'Confirm Selected Sources').click());
    assert.match(ui.host.querySelector('[role=alert]').textContent, /could not be isolated safely/);
    assert.equal(ui.changed(), 0);
  } finally { await ui.close(); }
});
test('exact previews require explicit selection and confirmation; outline is the default', async () => {
  const calls = []; const ui = await mount({ getSources: async () => ({ version: 2, source }), confirmSources: async (...args) => calls.push(args) });
  try {
    assert.equal(button(ui, 'Confirm Selected Sources').disabled, true); assert.equal(calls.length, 0);
    assert.match(ui.host.textContent, /Outline-only plans|outline-only/);
    assert.equal(ui.host.querySelector('pre').textContent, 'Chapter 1\nOutline\n');
    const preview = ui.host.querySelector('pre').closest('details');
    const presentation = preview.querySelector('select');
    assert.equal(presentation.value, 'readable');
    await act(async () => {
      presentation.value = 'exact';
      presentation.dispatchEvent(new Event('change', { bubbles: true }));
    });
    assert.equal(preview.querySelector('pre').textContent, 'Chapter 1\nOutline\n');
    assert.equal(preview.querySelector('strong').textContent, 'Page 1, characters 0–18');
    assert.equal(button(ui, 'Confirm Selected Sources').disabled, true);
    await act(async () => ui.host.querySelector('input[type=checkbox]').click());
    await act(async () => button(ui, 'Confirm Selected Sources').click());
    assert.deepEqual(calls, [[7, 2, undefined, [{ fileId: 3, sectionIds: ['one'], instructional: false }], 'SOURCE_GROUNDED']]); assert.equal(ui.changed(), 1);
  } finally { await ui.close(); }
});
test('revision version is sent; duplicate clicks are locked and errors do not confirm', async () => {
  let resolve, count = 0;
  const ui = await mount({ getSources: async () => ({ version: 9, source }), extractSources: (id, version, revision) => { assert.deepEqual([id, version, revision], [7, 9, 9]); count++; return new Promise(r => { resolve = r; }); } }, { revisionVersion: 9 });
  try {
    await act(async () => { button(ui, 'Extract / Refresh Sources').click(); button(ui, 'Extract / Refresh Sources').click(); });
    assert.equal(count, 1); assert.equal(ui.changed(), 0);
    await act(async () => resolve()); assert.equal(ui.changed(), 1);
  } finally { await ui.close(); }
  const error = await mount({ getSources: async () => ({ version: 1, source: null }), extractSources: async () => { throw { response: { data: { code: 'SOURCE_EXTRACTION_FAILED' } } }; } });
  try { await act(async () => button(error, 'Extract / Refresh Sources').click()); assert.match(error.host.querySelector('[role=alert]').textContent, /Source extraction failed/); assert.equal(error.changed(), 0); } finally { await error.close(); }
});
test('preview never fetches or writes sources and unsaved materials disable mutations', async () => {
  let calls = 0; const api = { getSources: async () => { calls++; return { version: 1, source }; } };
  const preview = await mount(api, {}, true); assert.equal(preview.host.textContent, ''); assert.equal(calls, 0); await preview.close();
  const disabled = await mount(api, { disabled: true });
  try { assert.equal(button(disabled, 'Extract / Refresh Sources').disabled, true); assert.equal(disabled.host.querySelector('fieldset').disabled, true); } finally { await disabled.close(); }
});
test('saved confirmation survives reload; changing choices or mode requires confirmation again', async () => {
  const saved = { ...source, confirmedBy: 5, mode: 'OBTL_EXPANSION', choices: [{ fileId: 3, sectionIds: ['one'], instructional: false }] };
  for (let i = 0; i < 2; i++) {
    const ui = await mount({ getSources: async () => ({ version: 4, source: saved }) });
    try {
      assert.equal(button(ui, 'Sources Confirmed').disabled, true);
      assert.equal(ui.host.querySelector('select').value, 'OBTL_EXPANSION');
      await act(async () => ui.host.querySelector('input[type=checkbox]').click());
      assert.ok(button(ui, 'Confirm Selected Sources'));
      await act(async () => ui.host.querySelector('input[type=checkbox]').click());
      assert.equal(button(ui, 'Sources Confirmed').disabled, true);
      await act(async () => { const select = ui.host.querySelector('select'); select.value = 'SOURCE_GROUNDED'; select.dispatchEvent(new Event('change', { bubbles: true })); });
      assert.equal(button(ui, 'Confirm Selected Sources').disabled, false);
    } finally { await ui.close(); }
  }
});
test('switching a saved Source-Grounded outline confirms expansion and restores it after remount', async () => {
  let saved = { ...source, mode: 'SOURCE_GROUNDED', confirmedBy: 5, choices: [{ fileId: 3, sectionIds: ['one'], instructional: false }] };
  const calls = [];
  const api = {
    getSources: async () => ({ version: 4, source: saved }),
    confirmSources: async (...args) => {
      calls.push(args);
      saved = { ...saved, mode: args[4], choices: args[3], instructionVerified: false, review: null };
    },
  };
  const ui = await mount(api);
  try {
    assert.equal(button(ui, 'Sources Confirmed').disabled, true);
    await act(async () => { const select = ui.host.querySelector('select'); select.value = 'OBTL_EXPANSION'; select.dispatchEvent(new Event('change', { bubbles: true })); });
    assert.equal(saved.mode, 'SOURCE_GROUNDED');
    assert.equal(calls.length, 0);
    assert.equal(button(ui, 'Confirm Selected Sources').disabled, false);
    await act(async () => button(ui, 'Confirm Selected Sources').click());
    assert.deepEqual(calls, [[7, 4, undefined, [{ fileId: 3, sectionIds: ['one'], instructional: false }], 'OBTL_EXPANSION']]);
    assert.equal(ui.changed(), 1);
  } finally { await ui.close(); }
  const restored = await mount(api);
  try {
    assert.equal(restored.host.querySelector('select').value, 'OBTL_EXPANSION');
    assert.equal(button(restored, 'Sources Confirmed').disabled, true);
    assert.equal(button(restored, 'Save Academic Review'), undefined);
  } finally { await restored.close(); }
});
test('outline expansion is explicit and academic acknowledgment is required before saving review', async () => {
  const calls = [];
  const saved = { ...source, confirmedBy: 5, mode: 'OBTL_EXPANSION', instructionVerified: true, choices: [{ fileId: 3, sectionIds: ['one'], instructional: false }] };
  const lesson = { objectives: 'Learn spices', knowledge: 'Instruction', examples: 'Scenario', summary: 'Summary' };
  const ui = await mount({ getSources: async () => ({ version: 4, source: saved, lesson }), reviewExpansion: async (...args) => calls.push(args) });
  try {
    assert.match(ui.host.textContent, /not text extracted from the OBTL/);
    assert.equal(ui.host.querySelectorAll('textarea').length, 4);
    assert.equal(button(ui, 'Save Academic Review').disabled, true);
    const check = [...ui.host.querySelectorAll('input[type=checkbox]')].at(-1);
    await act(async () => check.click());
    await act(async () => button(ui, 'Save Academic Review').click());
    assert.deepEqual(calls, [[7, 4, undefined, lesson, true]]);
    assert.equal(ui.changed(), 1);
  } finally { await ui.close(); }
});

test('filtering a selected chapter out of view preserves the confirmation payload', async () => {
  const calls = [];
  const ui = await mount({getSources: async () => ({version:4,source}), confirmSources: async (...args) => calls.push(args)});
  try {
    await act(async () => ui.host.querySelector('input[type=checkbox]').click());
    await act(async () => {
      const filter = ui.host.querySelectorAll('select')[1];
      filter.value = 'Examinations';
      filter.dispatchEvent(new Event('change', {bubbles:true}));
    });
    assert.match(ui.host.textContent, /No matching sections/);
    await act(async () => button(ui,'Confirm Selected Sources').click());
    assert.deepEqual(calls[0][3], [{fileId:3, sectionIds:['one'], instructional:false}]);
  } finally { await ui.close(); }
});

test('ABTT separates recoverable chapters, partial excerpts, unresolved pages and exams', {skip: !process.env.HALO_ABTT_EXTRACTION}, async () => {
  const {readFile} = await import('node:fs/promises');
  const extraction = JSON.parse(await readFile(process.env.HALO_ABTT_EXTRACTION, 'utf8'));
  const ui = await mount({getSources: async () => ({version: 1, source: {...source, documents: [{fileId: 3, name: 'ABTT.pdf', extraction}]}})});
  try {
    const group = name => [...ui.host.querySelectorAll('h4')].find(h => h.textContent.startsWith(name)).parentElement;
    const cards = element => [...element.querySelectorAll('label > input[type=checkbox]')];
    const ready = cards(group('Ready for Selection'));
    for (const number of [2, 8, 10]) {
      const item = ready.find(input => input.parentElement.textContent.trim().startsWith('Chapter ' + number));
      assert.equal(item.disabled, false);
      assert.match(item.parentElement.parentElement.textContent, /Recoverable row/);
    }
    const detected = group('Detected');
    assert.ok(cards(detected).length > 0 && cards(detected).every(input => input.disabled));
    assert.match(detected.textContent, /Partial excerpt/);
    assert.doesNotMatch(detected.textContent, /Complete chapter boundaries/);
    assert.ok(group('Unresolved PDF Regions').querySelector('label select option[value^="region-"]'));
    assert.equal(cards(group('Examinations and Assessments')).length, 2);
  } finally { await ui.close(); }
});

test('standard workbook cards preserve worksheet evidence and explicit selection', async () => {
  const calls=[];
  const row={id:'SEC-001',type:'WEEK',title:'Introduction to Art Appreciation',complete:true,
    definition:{row:6,cells:['SEC-001','1','WEEK','1','1','Week 1','','Introduction to Art Appreciation','Week 1','2','Complete','Pending professor review']},
    content:[{row:6,cells:['SEC-001','1','Introduction to Art Appreciation']}]};
  const ui=await mount({getSources:async()=>({version:2,source:{...source,documents:[{fileId:3,name:'OBTL.xlsx',workbook:{sections:[row]}}]}}), confirmSources:async(...args)=>calls.push(args)});
  try {
    assert.match(ui.host.textContent,/SECTIONS row 6/);
    assert.match(ui.host.textContent,/CONTENT row 6/);
    assert.match(ui.host.textContent,/Pending professor review/);
    assert.equal(ui.host.querySelector('pre').textContent,row.content[0].cells[2]);
    assert.equal(button(ui,'Confirm Selected Sources').disabled,true);
    await act(async()=>ui.host.querySelector('fieldset input[type=checkbox]').click());
    await act(async()=>button(ui,'Confirm Selected Sources').click());
    assert.deepEqual(calls[0][3],[{fileId:3,sectionIds:['SEC-001'],instructional:false}]);
  } finally {await ui.close();}
});

test('exam guide requires explicit coverage; selecting the exam never preselects chapters', async () => {
  const calls=[];
  const sections=[['SEC-002','CHAPTER','Management'],['SEC-007','EXAMINATION','MIDTERM EXAMINATION'],['SEC-008','CHAPTER','Property Management System']].map(([id,type,title])=>({
    id,type,title,complete:true,definition:{row:8,cells:[id,'1',type,'9','9','Week 9','','','Week 9']},
    content:[{row:8,cells:[id,'1',title]}]
  }));
  const ui=await mount({getSources:async()=>({version:3,source:{...source,documents:[{fileId:3,name:'ABTT.xlsx',workbook:{sections}}]}}),confirmSources:async(...args)=>calls.push(args)});
  try {
    const label=text=>[...ui.host.querySelectorAll('label')].find(l=>l.textContent===text);
    await act(async()=>label('MIDTERM EXAMINATION').querySelector('input').click());
    const purpose=[...ui.host.querySelectorAll('select')].find(s=>s.value==='RECORD_ONLY');
    assert.ok(purpose);
    await act(async()=>{purpose.value='REVIEW_GUIDE';purpose.dispatchEvent(new Event('change',{bubbles:true}));});
    assert.equal(button(ui,'Confirm Selected Sources').disabled,true);
    const exam=purpose.closest('fieldset');
    const coverage=exam.querySelectorAll('input[type=checkbox]');
    assert.ok([...coverage].every(input=>!input.checked));
    const preview=exam.querySelector('details');
    await act(async()=>preview.querySelector('summary').click());
    assert.equal(preview.open,true);
    assert.equal(exam.querySelector('dd').textContent,'Management');
    assert.equal(button(ui,'Confirm Selected Sources').disabled,true);
    await act(async()=>coverage[1].click());
    await act(async()=>preview.querySelector('summary').click());
    const filter=ui.host.querySelectorAll('select')[1];
    await act(async()=>{filter.value='Examinations';filter.dispatchEvent(new Event('change',{bubbles:true}));});
    await act(async()=>button(ui,'Confirm Selected Sources').click());
    assert.deepEqual(calls[0][3][0].examination,{purpose:'REVIEW_GUIDE',coverageIds:['SEC-008']});
  } finally {await ui.close();}
});
test('Art Appreciation pagination retains every selected section in confirmation', async () => {
  // The SECTIONS worksheet in HALO_OBTL_Art_Appreciation_Filled.xlsx.
  const titles = ["Introduction to Art Appreciation","Art, Artists, and the Audience","The Elements of Art","Principles of Art","Art Criticism and Art Appreciation","Visual Arts: Painting and Drawing","Sculpture and Architecture","MIDTERM EXAM","Performing Arts: Music, Dance, and Theater","Literary and Digital Arts","Art and Culture","Philippine Art and Artists","Indigenous and Traditional Philippine Arts","Art Movements and Styles","Modern and Contemporary Art","Art, Identity, and Society","FINAL EXAM"];
  const sections = titles.map((title,index) => {
    const week=index+1, id='SEC-'+String(week).padStart(3,'0'),type=[8,17].includes(week)?'EXAMINATION':'WEEK';
    return {id,title,type,complete:true,content:[],definition:{row:week+5,cells:[id,week,type,week,week,'Week '+week,'',title,'Week '+week]}};
  });
  const calls=[];
  const ui=await mount({getSources:async()=>({version:4,source:{documents:[{fileId:3,name:'HALO_OBTL_Art_Appreciation_Filled.xlsx',workbook:{sections}}]}}),confirmSources:async(...args)=>calls.push(args)});
  try {
    const labels=()=>[...ui.host.querySelectorAll('label')];
    const box=i=>labels().find(l=>l.textContent===titles[i])?.querySelector('input');
    const click=el=>act(async()=>el.click());
    const select=async(label,value)=>{const el=labels().find(l=>l.textContent.startsWith(label)).querySelector('select');await act(async()=>{el.value=value;el.dispatchEvent(new Event('change',{bubbles:true}));});};
    assert.match(ui.host.textContent,/Showing 1–5 of 17 sections · Page 1 of 4/);
    assert.ok(button(ui,'Previous').disabled);
    await click(box(0)); await click(button(ui,'Next')); await click(box(6));
    assert.ok(box(7).disabled);
    await click(button(ui,'Previous')); assert.ok(box(0).checked);
    await click(ui.host.querySelector('[aria-label="Page 4"]')); assert.ok(button(ui,'Next').disabled);
    await select('Sections per page','10'); assert.match(ui.host.textContent,/Showing 1–10 of 17/);
    assert.ok(box(6).checked);
    await select('Sections per page','20'); assert.equal(button(ui,'Next'),undefined);
    await select('Show','Examinations'); assert.ok(box(7).disabled && box(16).disabled);
    await select('Show','Chapters'); assert.match(ui.host.textContent,/No matching sections/);
    await click(button(ui,'Confirm Selected Sources'));
    assert.deepEqual(calls[0][3],[{fileId:3,sectionIds:['SEC-001','SEC-007'],instructional:false}]);
  } finally { await ui.close(); }
});
