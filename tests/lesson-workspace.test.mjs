import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';

let dom, server, createRoot, Router, Editor, Context, Auth, Toast;
before(async () => {
  dom = new JSDOM('<html><body></body></html>', { url: 'http://localhost' });
  for (const key of ['window', 'document', 'HTMLElement', 'Event', 'localStorage', 'sessionStorage']) globalThis[key] = dom.window[key];
  globalThis.IS_REACT_ACT_ENVIRONMENT = true;
  ({ createRoot } = await import('react-dom/client'));
  Router = await import('react-router-dom');
  server = await createServer({ server: { middlewareMode: true }, appType: 'custom' });
  Editor = (await server.ssrLoadModule('/src/pages/professor/components/LessonEditor.jsx')).default;
  ({ ProfessorWorkflowContext: Context } = await server.ssrLoadModule('/src/context/professor/useProfessorWorkflow.js'));
  ({ AuthContext: Auth } = await server.ssrLoadModule('/src/context/login/useAuth.js'));
  ({ ToastContext: Toast } = await server.ssrLoadModule('/src/context/notifications/useToast.js'));
});
after(async () => { await server.close(); dom.window.close(); });
const lesson = { id: 37, subjectId: 7, weekId: 80, weekNumber: 3, status: 'APPROVED', aiGenerationStatus: 'COMPLETED', lessonText: 'Live lesson', aiNotes: 'Live notes', youtubeLink: '', generatedObjectives: 'Live objectives', generatedKnowledge: 'Live knowledge', generatedExamples: 'Live examples', generatedSummary: 'Live summary', files: [{ id: 3, originalFileName: 'Original.pdf' }] };
const draft = { version: 8, revisionNumber: 2, state: 'DRAFT', lessonText: 'Saved revision', aiNotes: '', youtubeLink: '', files: [] };
const quiz = { current: { id: 2, version: 4, questions: [] }, draft: null };
const empty = { content: [], totalPages: 0, last: true };
const h = React.createElement;
const button = label => [...document.querySelectorAll('button')].find(node => node.textContent.trim() === label && !node.closest('[hidden]'));
const tab = label => [...document.querySelectorAll('[role="tab"]')].find(node => node.textContent.trim() === label);
async function click(label) { const node = button(label); assert.ok(node, label); await act(async () => node.click()); }
async function fill(node, value) { await act(async () => { Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value').set.call(node, value); node.dispatchEvent(new Event('input', { bubbles: true })); }); }
async function mount(overrides = {}, mode = {}) {
  const calls = [];
  const api = { getLearningModuleByWeek: async () => lesson, getRevision: async () => null, getQuizRevision: async () => { calls.push('quiz'); return quiz; }, getRevisionHistory: async () => { calls.push('history'); return empty; }, ...overrides };
  const host = document.createElement('div'); document.body.append(host); const root = createRoot(host);
  const workflow = { api, basePath: '/professor', ...mode };
  await act(async () => root.render(h(Auth.Provider, { value: { user: { id: 1, name: 'Real user', role: mode.acting ? 'ADMIN' : 'PROFESSOR' }, logout() {} } },
    h(Toast.Provider, { value: { showToast() {} } }, h(Context.Provider, { value: workflow },
      h(Router.MemoryRouter, { initialEntries: ['/subjects/7/weeks/80'] }, h(Router.Routes, {}, h(Router.Route, { path: '/subjects/:subjectId/weeks/:weekId', element: h(Editor) }))))))));
  return { host, calls, close: async () => { await act(async () => root.unmount()); host.remove(); } };
}

test('workspace has accessible keyboard tabs, published content is read-only and long sections collapse', async () => {
  const ui = await mount();
  try {
    assert.match(ui.host.textContent, /Subject #7 · Week 3/);
    assert.equal(tab('Lesson').getAttribute('aria-selected'), 'true');
    assert.equal(ui.host.querySelectorAll('textarea').length, 0);
    assert.equal(ui.host.querySelectorAll('details[open]').length, 0);
    assert.ok(button('Revise Lesson')); assert.deepEqual(ui.calls, []);
    const tabs = ['Lesson', 'Assessment', 'Revision History'];
    for (const label of tabs) assert.ok(document.getElementById(tab(label).getAttribute('aria-controls')));
    tab('Lesson').focus();
    await act(async () => tab('Lesson').dispatchEvent(new window.KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true })));
    assert.equal(document.activeElement, tab('Assessment')); assert.equal(tab('Assessment').tabIndex, 0);
    assert.deepEqual(ui.calls, ['quiz']);
    await act(async () => tab('Assessment').dispatchEvent(new window.KeyboardEvent('keydown', { key: 'End', bubbles: true })));
    assert.equal(document.activeElement, tab('Revision History')); assert.deepEqual(ui.calls, ['quiz', 'history']);
    assert.match(ui.host.textContent, /No published revision history/);
    await act(async () => tab('Revision History').dispatchEvent(new window.KeyboardEvent('keydown', { key: 'Home', bubbles: true })));
    assert.equal(document.activeElement, tab('Lesson'));
    assert.equal(ui.host.querySelectorAll('[role="tabpanel"]:not([hidden])').length, 1);
  } finally { await ui.close(); }
});

test('unsaved revision fields and files survive tab switches, preserving version and save payload', async () => {
  let saved; const file = new window.File(['PDF'], 'New.pdf', { type: 'application/pdf' });
  const ui = await mount({ getRevision: async () => draft, saveRevision: async (id, payload, files) => { saved = { id, payload, files }; return { ...draft, ...payload.materials, version: 9, files: [{ key: 'new', originalFileName: 'New.pdf' }] }; } });
  try {
    const text = ui.host.querySelector('#revision-text'); await fill(text, 'Unsaved revision');
    const input = ui.host.querySelector('#revision-files'); Object.defineProperty(input, 'files', { configurable: true, value: [file] });
    await act(async () => input.dispatchEvent(new Event('change', { bubbles: true })));
    await click('Assessment'); await click('Revision History'); await click('Lesson');
    assert.equal(ui.host.querySelector('#revision-text'), text); assert.equal(text.value, 'Unsaved revision');
    assert.equal(button('Review Changes').disabled, true); assert.equal(button('Publish Revision').disabled, true);
    assert.equal(saved, undefined); await click('Save Draft');
    assert.equal(saved.id, 37); assert.equal(saved.payload.version, 8); assert.equal(saved.payload.materials.lessonText, 'Unsaved revision'); assert.equal(saved.files[0], file);
    assert.match(ui.host.textContent, /Live lesson/); assert.equal(draft.lessonText, 'Saved revision');
  } finally { await ui.close(); }
});

test('initial lesson material edits also survive tabs without creating or publishing a module', async () => {
  const ui = await mount({ getLearningModuleByWeek: async () => null });
  try {
    await fill(ui.host.querySelector('#lesson-text'), 'Unpublished new lesson');
    await click('Assessment'); assert.match(ui.host.textContent, /Publish the lesson before managing/);
    await click('Revision History'); await click('Lesson');
    assert.equal(ui.host.querySelector('#lesson-text').value, 'Unpublished new lesson'); assert.deepEqual(ui.calls, []);
  } finally { await ui.close(); }
});

test('returning to history refreshes snapshots without resetting the lesson draft', async () => {
  let reads = 0;
  const ui = await mount({ getRevision: async () => draft, getRevisionHistory: async () => {
    reads++;
    return reads === 1 ? empty : { content: [{ revisionNumber: 0, snapshotKind: 'INITIAL_PUBLISHED' }], totalPages: 1, last: true };
  } });
  try {
    const input = ui.host.querySelector('#revision-text'); await fill(input, 'Keep these edits');
    await click('Revision History'); assert.match(ui.host.textContent, /No published revision history/);
    await click('Lesson'); await click('Revision History');
    assert.equal(reads, 2); assert.match(ui.host.textContent, /Initial published baseline/);
    await click('Lesson'); assert.equal(input.value, 'Keep these edits');
  } finally { await ui.close(); }
});

test('assessment retry and reviewed quiz state survive tab navigation without extra requests', async () => {
  let count = 0;
  const ui = await mount({ getQuizRevision: async () => { if (++count === 1) throw { response: { status: 503 } }; return { ...quiz, draft: { id: 5, version: 5, questions: [] } }; } });
  try {
    await click('Assessment'); assert.ok(button('Retry')); await click('Retry'); assert.equal(count, 2);
    await click('Review Quiz Draft'); assert.equal(button('Approve Quiz').disabled, false);
    await click('Lesson'); await click('Assessment'); assert.equal(count, 2); assert.equal(button('Approve Quiz').disabled, false);
    await click('Approve Quiz'); assert.match(document.querySelector('[role="dialog"]').textContent, /Approve Quiz\?/);
    await click('Cancel'); assert.equal(document.querySelector('[role="dialog"]'), null);
  } finally { await ui.close(); }
});

test('revision start/discard remain explicit confirmations and transfer focus into the workspace', async () => {
  let starts = 0, discards = 0;
  const ui = await mount({ startRevision: async () => { starts++; return draft; }, discardRevision: async (id, version) => { assert.equal(id, 37); assert.equal(version, 8); discards++; } });
  try {
    button('Revise Lesson').focus(); await click('Revise Lesson'); await click('Cancel'); assert.equal(starts, 0);
    await click('Revise Lesson'); await click('Create Revision'); assert.equal(starts, 1);
    assert.equal(document.activeElement.getAttribute('aria-label'), 'Published lesson revision');
    await click('Discard Revision'); assert.equal(discards, 0);
    const action = [...document.querySelectorAll('[role="dialog"] button')].find(b => b.textContent === 'Discard Revision');
    await act(async () => action.click()); assert.equal(discards, 1); assert.ok(button('Revise Lesson'));
  } finally { await ui.close(); }
});

test('Admin Preview stays read-only and Acting uses its supplied workflow API', async () => {
  const ui = await mount({ getRevision() { assert.fail('Preview revision API'); }, getQuizRevision() { assert.fail('Preview quiz API'); }, getRevisionHistory() { assert.fail('Preview history API'); } }, { acting: true, preview: true, target: { name: 'Selected Professor' }, basePath: '/admin/professor-preview', leave() {} });
  try {
    assert.equal(button('Revise Lesson'), undefined); assert.equal(ui.host.querySelectorAll('textarea').length, 0);
    await click('Assessment'); await click('Revision History'); assert.match(ui.host.textContent, /not available in read-only Professor Preview/);
    assert.match(ui.host.textContent, /Real user/);
  } finally { await ui.close(); }
  let read = 0;
  const acting = await mount({ getRevision: async id => { assert.equal(id, 37); read++; return draft; } }, { acting: true, target: { name: 'Selected Professor' }, basePath: '/admin/professor-mode', leave() {} });
  try { assert.equal(read, 1); assert.ok(button('Save Draft')); assert.match(acting.host.textContent, /Changes affect real academic data and are audited/); }
  finally { await acting.close(); }
});
