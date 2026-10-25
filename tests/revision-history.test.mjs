import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';

let dom, server, createRoot, History, Revision, Tabs, Context, serviceFactory, actingFactory, apiClient, session;
before(async () => {
  dom = new JSDOM('<html><body></body></html>', { url: 'http://localhost' });
  for (const key of ['window', 'document', 'HTMLElement', 'Event', 'localStorage', 'sessionStorage']) globalThis[key] = dom.window[key];
  globalThis.IS_REACT_ACT_ENVIRONMENT = true;
  ({ createRoot } = await import('react-dom/client'));
  server = await createServer({ server: { middlewareMode: true }, appType: 'custom' });
  History = (await server.ssrLoadModule('/src/pages/professor/components/RevisionHistory.jsx')).default;
  Revision = (await server.ssrLoadModule('/src/pages/professor/components/LessonRevision.jsx')).default;
  Tabs = (await server.ssrLoadModule('/src/pages/professor/components/LessonWorkspaceTabs.jsx')).default;
  ({ ProfessorWorkflowContext: Context } = await server.ssrLoadModule('/src/context/professor/useProfessorWorkflow.js'));
  ({ createProfessorService: serviceFactory } = await server.ssrLoadModule('/src/services/professor/professorService.js'));
  ({ createActingProfessorScope: actingFactory } = await server.ssrLoadModule('/src/services/admin/professorModeService.js'));
  apiClient = (await server.ssrLoadModule('/src/services/apiClient.js')).default;
  session = await server.ssrLoadModule('/src/utils/session.js');
});
after(async () => { await server.close(); dom.window.close(); });

const snapshot = { revisionNumber: 1, snapshotKind: 'REVISION', publishedAt: '2026-10-18T14:47:25', recordedAt: '2026-10-18T14:47:26', revisedAt: null, revisedBy: 21, publishedBy: 20, publishedModuleVersion: 5 };
const page = (content = [snapshot], extra = {}) => ({ content, number: 0, totalPages: 1, totalElements: content.length, first: true, last: true, ...extra });
const details = (metadata = snapshot) => ({ metadata, lessonText: 'Original lesson <script>bad()</script>', aiNotes: 'Original notes', youtubeLink: 'https://youtu.be/dQw4w9WgXcQ', generatedObjectives: 'Objectives', generatedKnowledge: 'Knowledge', generatedExamples: 'Examples', generatedSummary: 'Summary', files: [{ key: 'one', originalFileName: 'Original.pdf' }] });
const button = text => [...document.querySelectorAll('button')].find(b => b.textContent.trim() === text);
const click = async text => { const target = typeof text === 'string' ? button(text) : text; assert.ok(target, `Button ${text} exists`); await act(async () => target.click()); };
const deferred = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no; }); return { promise, resolve, reject }; };
async function mount(api, options = {}) {
  const host = document.createElement('div'); document.body.append(host); const root = createRoot(host);
  const render = async (moduleId = 37, preview = false) => act(async () => root.render(React.createElement(Context.Provider, { value: { api, preview } }, options.revision
    ? React.createElement(Tabs, { key: moduleId, history: React.createElement(History, { moduleId, embedded: true }) }, React.createElement(Revision, { moduleId, onPublished() { throw Error('Unexpected publication'); } }))
    : React.createElement(History, { moduleId }))));
  await render(options.moduleId || 37, options.preview || false);
  return { host, render, close: async () => { await act(async () => root.unmount()); host.remove(); } };
}

test('history loads on demand and details are read-only, safely rendered and keyboard dismissible', async () => {
  const calls = [];
  const ui = await mount({ getRevisionHistory: async (...args) => { calls.push(['list', ...args]); return page(); }, getRevisionHistoryVersion: async (...args) => { calls.push(['details', ...args]); return details(); } });
  try {
    assert.equal(calls.length, 0); assert.equal(button('Revision History').getAttribute('aria-expanded'), 'false');
    await click('Revision History'); assert.equal(calls[0][1], 37); assert.equal(calls[0][2], 0); assert.equal(calls[0][3], 5);
    assert.match(ui.host.textContent, /Revision 1/); assert.match(ui.host.textContent, /Account ID 21/); assert.match(ui.host.textContent, /Account ID 20/);
    assert.match(ui.host.textContent, /18 Oct 2026|Oct 18, 2026/);
    const opener = button('View Details'); opener.focus(); await click(opener);
    const dialog = document.querySelector('[role="dialog"]'); assert.ok(dialog); assert.equal(calls[1][2], 1);
    for (const text of ['Original lesson', 'Original notes', 'Objectives', 'Knowledge', 'Examples', 'Summary', 'Original.pdf']) assert.ok(dialog.textContent.includes(text));
    assert.equal(dialog.querySelectorAll('input,textarea,select,[contenteditable="true"],script').length, 0);
    assert.deepEqual([...dialog.querySelectorAll('button')].map(b => b.textContent), ['Close details']);
    await act(async () => dialog.dispatchEvent(new window.KeyboardEvent('keydown', { key: 'Escape', bubbles: true })));
    assert.equal(document.querySelector('[role="dialog"]'), null); assert.equal(document.activeElement, opener);
  } finally { await ui.close(); }
});

test('legacy and initial baselines do not invent revision numbers, authors or publication dates', async () => {
  const baselines = ['LEGACY_LIVE_BASELINE', 'INITIAL_PUBLISHED'].map((snapshotKind, i) => ({ revisionNumber: i, snapshotKind, recordedAt: '2026-10-20T12:00:00', publishedAt: null, revisedBy: null, publishedBy: null, publishedModuleVersion: 0 }));
  const ui = await mount({ getRevisionHistory: async () => page(baselines), getRevisionHistoryVersion: async () => details(baselines[0]) });
  try {
    await click('Revision History'); assert.match(ui.host.textContent, /Legacy live baseline/); assert.match(ui.host.textContent, /Initial published baseline/);
    assert.doesNotMatch(ui.host.textContent, /Revision 0|Revision 1|Account ID null|undefined/);
    assert.match(ui.host.textContent, /Not recorded/); await click('View Details');
    assert.match(document.querySelector('[role="dialog"]').textContent, /not a recovered numbered revision/);
  } finally { await ui.close(); }
});

test('history pagination uses the server page and limits, with no duplicate or disabled requests', async () => {
  const requested = [];
  const ui = await mount({ getRevisionHistory: async (id, index, size) => { requested.push([id, index, size]); return page([{ ...snapshot, revisionNumber: index ? 1 : 2 }], { number: index, totalPages: 2, last: index === 1 }); } });
  try {
    await click('Revision History'); assert.equal(button('Previous').disabled, true); await click('Previous'); assert.equal(requested.length, 1);
    await click('Next'); assert.match(ui.host.textContent, /Page 2 of 2/); assert.match(ui.host.textContent, /Revision 1/); assert.equal(button('Next').disabled, true);
    await click('Previous'); assert.match(ui.host.textContent, /Page 1 of 2/); assert.deepEqual(requested, [[37, 0, 5], [37, 1, 5], [37, 0, 5]]);
  } finally { await ui.close(); }
});

test('empty history leaves saved Revision 2 and unsaved draft edits unchanged and performs GETs only', async () => {
  const saved = { version: 8, revisionNumber: 2, state: 'DRAFT', lessonText: 'Saved draft', youtubeLink: '', aiNotes: 'Notes', files: [] };
  const original = structuredClone(saved); const requests = [];
  const api = serviceFactory({ get: async (url, config) => { requests.push({ url, config }); return { data: url.endsWith('/history') ? page([]) : saved }; }, post() { assert.fail('Unexpected POST'); }, put() { assert.fail('Unexpected PUT'); }, delete() { assert.fail('Unexpected DELETE'); } });
  const ui = await mount(api, { revision: true });
  try {
    const input = ui.host.querySelector('textarea');
    await act(async () => { Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value').set.call(input, 'Unsaved draft edit'); input.dispatchEvent(new Event('input', { bubbles: true })); });
    await click('Revision History'); assert.match(ui.host.textContent, /No published revision history/); assert.equal(button('View Details'), undefined);
    await click('Lesson'); assert.equal(input.value, 'Unsaved draft edit'); assert.deepEqual(saved, original);
    assert.deepEqual(requests.map(r => r.url), ['/professor/ai-learning-modules/37/revision', '/professor/ai-learning-modules/37/revision/history']);
  } finally { await ui.close(); }
});

test('loading, close and module switching abort stale history and detail requests', async () => {
  const pending = deferred(); const lateDetails = deferred(); let listSignal, detailSignal;
  const ui = await mount({ getRevisionHistory: async (id, _page, _size, config) => { if (id === 37) { listSignal = config.signal; return pending.promise; } return page([{ ...snapshot, revisionNumber: 4 }]); }, getRevisionHistoryVersion: async (_id, _number, config) => { detailSignal = config.signal; return lateDetails.promise; } });
  try {
    await click('Revision History'); assert.match(ui.host.textContent, /Loading revision history/);
    await ui.render(38); assert.equal(listSignal.aborted, true); await act(async () => pending.resolve(page()));
    assert.doesNotMatch(ui.host.textContent, /Revision 1/); await click('Revision History'); assert.match(ui.host.textContent, /Revision 4/);
    await click('View Details'); assert.match(document.body.textContent, /Loading revision details/); await click('Close details'); assert.equal(detailSignal.aborted, true);
    await act(async () => lateDetails.resolve(details())); assert.equal(document.querySelector('[role="dialog"]'), null); assert.doesNotMatch(ui.host.textContent, /Original lesson/);
  } finally { await ui.close(); }
});

test('history errors are safe, distinguish authorization, and offer a GET retry', async () => {
  for (const status of [401, 403, 404, 500]) {
    let calls = 0;
    const ui = await mount({ getRevisionHistory: async () => { if (++calls === 1) throw { response: { status, data: { message: 'SECRET SQL' } } }; return page(); } });
    try {
      await click('Revision History'); assert.ok(ui.host.querySelector('[role="alert"]')); assert.doesNotMatch(ui.host.textContent, /SECRET SQL/);
      if (status === 403) assert.match(ui.host.textContent, /permission/);
      if (status === 401) assert.match(ui.host.textContent, /sign in/);
      await click('Retry history'); assert.equal(calls, 2); assert.match(ui.host.textContent, /Revision 1/);
    } finally { await ui.close(); }
  }
});

test('detail errors keep the list intact and can be retried or closed', async () => {
  let calls = 0;
  const ui = await mount({ getRevisionHistory: async () => page(), getRevisionHistoryVersion: async () => { if (++calls === 1) throw { code: 'ERR_NETWORK' }; return details(); } });
  try {
    await click('Revision History'); await click('View Details'); assert.match(document.body.textContent, /Cannot reach the server/);
    await click('Retry details'); assert.equal(calls, 2); assert.match(document.body.textContent, /Original lesson/); await click('Close details'); assert.match(ui.host.textContent, /Revision 1/);
  } finally { await ui.close(); }
});

test('Professor Preview exposes no history control or fallback request', async () => {
  const ui = await mount({ getRevisionHistory() { assert.fail('Preview cannot call an unexposed history route'); } }, { preview: true });
  try { assert.equal(ui.host.textContent, ''); } finally { await ui.close(); }
});

test('history services preserve authorization and Admin acting scope using GET only', async () => {
  const requests = []; const previous = apiClient.defaults.adapter;
  session.writeSession({ id: 20, name: 'Admin', email: 'admin@example.test', role: 'ADMIN' }, 'test-admin-token', false);
  apiClient.defaults.adapter = async config => { requests.push(config); return { data: config.url.endsWith('/history') ? page() : details(), status: 200, statusText: 'OK', headers: {}, config }; };
  const scope = actingFactory('authorized-session', () => assert.fail('Unexpected invalid session'));
  try {
    await scope.api.getRevisionHistory(37, 2, 5); await scope.api.getRevisionHistoryVersion(37, 0);
    assert.deepEqual(requests.map(r => r.url), ['/admin/acting/professor/ai-learning-modules/37/revision/history', '/admin/acting/professor/ai-learning-modules/37/revision/history/0']);
    assert.deepEqual(requests[0].params, { page: 2, size: 5 });
    assert.ok(requests.every(r => r.method === 'get' && r.headers.get('Authorization') === 'Bearer test-admin-token' && r.headers.get('X-Acting-Session') === 'authorized-session'));
  } finally { scope.dispose(); apiClient.defaults.adapter = previous; localStorage.clear(); sessionStorage.clear(); }
});
