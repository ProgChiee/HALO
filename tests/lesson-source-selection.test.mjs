import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';
import { professorErrorMessage } from '../src/utils/professorErrors.js';
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
  server = await createServer({ server: { middlewareMode: true }, appType: 'custom' });
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
