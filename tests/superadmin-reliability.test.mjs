import { test, before, after, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';

let server, dom, createRoot, root, host, api, Admins, AuthContext, ToastContext, MemoryRouter, useRemoteData;
let calls, feedback, viewportWidth, mediaListeners;
const deferred = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no; }); return { promise, resolve, reject }; };
before(async () => {
  dom = new JSDOM('<!doctype html><html><body></body></html>', { url: 'http://localhost/' });
  for (const key of ['window', 'document', 'HTMLElement', 'Event', 'MouseEvent', 'KeyboardEvent', 'localStorage', 'sessionStorage']) globalThis[key] = dom.window[key];
  globalThis.IS_REACT_ACT_ENVIRONMENT = true;
  ({ createRoot } = await import('react-dom/client'));
  server = await createServer({ server: { middlewareMode: true }, appType: 'custom' });
  ({ useRemoteData } = await server.ssrLoadModule('/src/hooks/useRemoteData.js'));
  Admins = (await server.ssrLoadModule('/src/pages/superadmin/components/Admins.jsx')).default;
  api = (await server.ssrLoadModule('/src/services/apiClient.js')).default;
  ({ AuthContext } = await server.ssrLoadModule('/src/context/login/useAuth.js'));
  ({ ToastContext } = await server.ssrLoadModule('/src/context/notifications/useToast.js'));
  ({ MemoryRouter } = await import('react-router-dom'));
});
beforeEach(() => {
  viewportWidth = 1440; mediaListeners = new Set();
  window.matchMedia = () => ({
    get matches() { return viewportWidth <= 1024; },
    addEventListener: (_event, listener) => mediaListeners.add(listener),
    removeEventListener: (_event, listener) => mediaListeners.delete(listener),
  });
  host = document.createElement('div'); document.body.append(host); root = createRoot(host);
  calls = []; feedback = [];
  api.defaults.adapter = config => {
    const pending = deferred();
    calls.push({ config, resolve: data => pending.resolve({ data, status: 200, headers: {}, config }), reject: pending.reject });
    return pending.promise;
  };
});
afterEach(async () => { await act(async () => root.unmount()); host.remove(); });
after(async () => { await server.close(); dom.window.close(); });
const click = async element => act(async () => { element.focus(); element.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true })); });
const button = text => [...document.querySelectorAll('button')].find(b => b.textContent.trim() === text);
const form = () => document.querySelector('form');
async function mountAdmins() {
  await act(async () => root.render(React.createElement(MemoryRouter, {},
    React.createElement(AuthContext.Provider, { value: { user: { name: 'Super Admin' }, logout() {} } },
      React.createElement(ToastContext.Provider, { value: { showToast: (...args) => feedback.push(args) } }, React.createElement(Admins))))));
}
async function fill() {
  const values = ['Test Admin', 'admin@example.test', 'test-password-12'];
  for (const [i, input] of [...form().querySelectorAll('input')].entries()) {
    await act(async () => {
      Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set.call(input, values[i]);
      input.dispatchEvent(new Event('input', { bubbles: true }));
    });
  }
}
async function submitTwice() {
  await act(async () => {
    form().dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    form().dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
  });
}

test('Add Admin locks every dismissal/reopen path and double submit; success resets once and reloads', async () => {
  await mountAdmins(); await act(async () => calls[0].resolve([]));
  await click(button('Add Admin')); await fill(); await submitTwice();
  assert.equal(calls.filter(c => c.config.method === 'post').length, 1);
  assert.equal(form().querySelector('[type=submit]').disabled, true);
  assert.equal(document.querySelector('[aria-label=Close]').disabled, true);
  assert.equal(button('Cancel').disabled, true);
  assert.equal(button('Add Admin').disabled, true);
  await click(document.querySelector('[aria-label=Close]')); await click(button('Cancel'));
  await click(form().parentElement.parentElement);
  await act(async () => form().dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })));
  await click(button('Add Admin'));
  assert.ok(form()); assert.equal(form().querySelector('input').value, 'Test Admin');
  await act(async () => calls[1].resolve({ name: 'Test Admin' }));
  assert.equal(form(), null); assert.equal(feedback.length, 1); assert.equal(calls.length, 3);
  assert.equal(button('Add Admin').disabled, true);
  await act(async () => calls[2].resolve([{ id: 1, name: 'Test Admin', status: 'ACTIVE' }]));
  assert.ok(host.textContent.includes('Test Admin')); await click(button('Add Admin'));
  assert.ok([...form().querySelectorAll('input')].every(input => input.value === ''));
});

test('failed/ambiguous create keeps values and modal, unlocks dismissal, and never retries POST', async () => {
  await mountAdmins(); await act(async () => calls[0].resolve([]));
  await click(button('Add Admin')); await fill(); await submitTwice();
  await act(async () => calls[1].reject({ code: 'ECONNABORTED' }));
  assert.ok(form()); assert.equal(form().querySelector('input').value, 'Test Admin');
  assert.ok(document.body.textContent.includes('took too long'));
  assert.equal(button('Cancel').disabled, false);
  assert.equal(calls.length, 2); assert.equal(feedback.length, 0);
  await click(button('Cancel')); assert.equal(form(), null);
  await click(button('Add Admin')); assert.equal(form().querySelector('input').value, '');
  assert.equal(document.body.textContent.includes('took too long'), false);
  await act(async () => form().dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })));
  assert.equal(form(), null);
});

test('old create completion cannot affect a newly mounted modal or send its success toast', async () => {
  await mountAdmins(); await act(async () => calls[0].resolve([]));
  await click(button('Add Admin')); await fill(); await submitTwice(); const old = calls[1];
  await act(async () => root.unmount()); root = createRoot(host); await mountAdmins();
  await act(async () => calls[2].resolve([])); await click(button('Add Admin'));
  await act(async () => old.resolve({ name: 'Old Admin' }));
  assert.ok(form()); assert.equal(form().querySelector('input').value, '');
  assert.equal(feedback.length, 0); assert.equal(calls.length, 3);
});

test('post-create authoritative GET wins over the initial pre-mutation GET', async () => {
  await mountAdmins(); const old = calls[0];
  await click(button('Add Admin')); await fill(); await submitTwice();
  await act(async () => calls[1].resolve({ name: 'New Admin' }));
  assert.equal(old.config.signal.aborted, true);
  await act(async () => calls[2].resolve([{ id: 8, name: 'New Admin', status: 'ACTIVE' }]));
  await act(async () => old.resolve([{ id: 2, name: 'Stale Admin', status: 'ACTIVE' }]));
  assert.ok(host.textContent.includes('New Admin')); assert.equal(host.textContent.includes('Stale Admin'), false);
});

test('status mutation performs one authoritative GET and never retries PATCH', async () => {
  await mountAdmins(); await act(async () => calls[0].resolve([{ id: 8, name: 'Admin', status: 'ACTIVE' }]));
  await click(host.querySelector('[aria-label=Deactivate]'));
  assert.equal(calls[1].config.method, 'patch');
  await act(async () => calls[1].resolve({ id: 8, status: 'INACTIVE' }));
  assert.equal(calls.length, 3); assert.equal(calls[2].config.method, 'get');
  assert.ok(host.textContent.includes('Loading admins'));
  await act(async () => calls[2].resolve([{ id: 8, name: 'Authoritative Admin', status: 'INACTIVE' }]));
  assert.ok(host.textContent.includes('Authoritative Admin')); assert.ok(host.querySelector('[aria-label=Activate]'));
});

async function hookHarness(loader) {
  const state = {};
  function Probe({ fetch }) { Object.assign(state, useRemoteData(fetch, [])); return null; }
  const render = async fetch => act(async () => root.render(React.createElement(Probe, { fetch })));
  await render(loader); return { state, render };
}

test('GET latest-request tracking ignores obsolete success/error even when loader ignores abort', async () => {
  const pending = [];
  const loader = ({ signal }) => { const d = deferred(); pending.push({ ...d, signal }); return d.promise; };
  const { state } = await hookHarness(loader);
  await act(async () => { void state.reload(); });
  assert.equal(pending[0].signal.aborted, true);
  await act(async () => pending[1].resolve(['new']));
  await act(async () => pending[0].reject(new Error('old error')));
  assert.deepEqual(state.data, ['new']); assert.equal(state.error, null); assert.equal(state.isLoading, false);
  await act(async () => { void state.reload(); });
  await act(async () => { void state.reload(); });
  await act(async () => pending[2].resolve(['old']));
  assert.equal(state.isLoading, true); assert.deepEqual(state.data, ['new']);
  await act(async () => pending[3].resolve(['newest']));
  assert.equal(state.isLoading, false); assert.deepEqual(state.data, ['newest']);
});

test('search/filter/page loader changes own loading state and only latest result can settle it', async () => {
  const requests = [deferred(), deferred(), deferred()]; const signals = [];
  const loaders = requests.map(d => ({ signal }) => { signals.push(signal); return d.promise; });
  const { state, render } = await hookHarness(loaders[0]);
  await act(async () => requests[0].resolve(['page 0']));
  assert.equal(state.isLoading, false);
  await render(loaders[1]); assert.equal(state.isLoading, true);
  await render(loaders[2]); assert.equal(signals[1].aborted, true);
  await act(async () => requests[1].reject(new Error('obsolete filter')));
  assert.equal(state.isLoading, true); assert.equal(state.error, null);
  await act(async () => requests[2].resolve(['latest filtered page']));
  assert.deepEqual(state.data, ['latest filtered page']); assert.equal(state.isLoading, false);
});


test('Monitoring preserves the focused search field during rapid paginated loads and keeps latest rows', async () => {
  const Monitoring = (await server.ssrLoadModule('/src/pages/admin/components/Monitoring.jsx')).default;
  await act(async () => root.render(React.createElement(MemoryRouter, {},
    React.createElement(AuthContext.Provider, { value: { role: 'superadmin', user: { name: 'Super Admin' }, logout() {} } },
      React.createElement(ToastContext.Provider, { value: { showToast() {} } }, React.createElement(Monitoring))))));
  const page = text => ({ content: [{ id: 1, userName: 'Actor', action: text, createdAt: '2026-01-01', activityType: 'ACCOUNT' }], number: 0, totalPages: 1, totalElements: 1 });
  await act(async () => calls[0].resolve(page('Initial row')));
  const search = host.querySelector('[placeholder="Search activity log"]'); search.focus();
  for (const value of ['a', 'ab']) {
    await act(async () => {
      Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set.call(search, value);
      search.dispatchEvent(new Event('input', { bubbles: true }));
    });
    assert.equal(document.activeElement, search);
  }
  assert.equal(calls[1].config.signal.aborted, true);
  await act(async () => calls[2].resolve(page('Latest row')));
  await act(async () => calls[1].resolve(page('Obsolete row')));
  assert.ok(host.textContent.includes('Latest row'));
  assert.equal(host.textContent.includes('Obsolete row'), false);
  assert.equal(document.activeElement, search);
});


async function resize(width) {
  await act(async () => {
    viewportWidth = width;
    for (const listener of [...mediaListeners]) listener({ matches: width <= 1024 });
  });
}
const key = async (element, key, shiftKey = false) => act(async () => element.dispatchEvent(new KeyboardEvent('keydown', { key, shiftKey, bubbles: true, cancelable: true })));

test('Super Admin shell switches at 1440/1024/768/480/375; drawer closes by backdrop, Escape and navigation', async () => {
  await mountAdmins(); await act(async () => calls[0].resolve([]));
  assert.ok(host.querySelector('aside')); assert.equal(button('Open navigation'), undefined);
  for (const width of [1024, 768, 480, 375]) {
    await resize(width);
    const opener = host.querySelector('[aria-label="Open navigation"]');
    assert.ok(opener); assert.equal(host.querySelector('aside'), null);
    await click(opener);
    const drawer = document.querySelector('[role=dialog]');
    assert.ok(drawer.querySelector('aside')); assert.equal(opener.getAttribute('aria-expanded'), 'true');
    assert.equal(host.hasAttribute('inert'), true); assert.equal(document.body.style.overflow, 'hidden');
    await click(drawer.parentElement);
    assert.equal(document.querySelector('[role=dialog]'), null); assert.equal(document.activeElement === opener, true);
    await click(opener); await key(document.activeElement, 'Escape');
    assert.equal(document.querySelector('[role=dialog]'), null);
    await click(opener); await click(document.querySelector('[role=dialog] a'));
    assert.equal(document.querySelector('[role=dialog]'), null);
    assert.equal(host.hasAttribute('inert'), false);
  }
  await resize(1440); assert.ok(host.querySelector('aside'));
  assert.equal(host.querySelector('[aria-label="Open navigation"]'), null);
});

test('resizing closes the drawer without remounting page content or reloading its data', async () => {
  await mountAdmins(); await act(async () => calls[0].resolve([]));
  await resize(375); await click(host.querySelector('[aria-label="Open navigation"]'));
  await resize(1440); assert.equal(document.querySelector('[role=dialog]'), null);
  await resize(375); assert.equal(document.querySelector('[role=dialog]'), null);
  assert.equal(calls.length, 1);
});

test('Add Admin dialog has labels, initial focus, focus trap, inert background and restored opener', async () => {
  await mountAdmins(); await act(async () => calls[0].resolve([]));
  const opener = button('Add Admin'); await click(opener);
  const dialog = document.querySelector('[role=dialog]');
  assert.equal(dialog.getAttribute('aria-modal'), 'true');
  assert.equal(document.getElementById(dialog.getAttribute('aria-labelledby')).textContent, 'Add new Admin');
  assert.equal(document.activeElement.id, 'admin-name');
  for (const input of dialog.querySelectorAll('input')) assert.ok(dialog.querySelector('label[for="' + input.id + '"]'));
  const close = dialog.querySelector('[aria-label=Close]'); const last = dialog.querySelector('[type=submit]');
  close.focus(); await key(close, 'Tab', true); assert.equal(document.activeElement === last, true);
  await key(last, 'Tab'); assert.equal(document.activeElement === close, true);
  opener.focus(); assert.ok(dialog.contains(document.activeElement));
  assert.equal(host.hasAttribute('inert'), true);
  await key(document.activeElement, 'Escape');
  assert.equal(document.querySelector('[role=dialog]'), null);
  assert.equal(document.activeElement === opener, true); assert.equal(host.hasAttribute('inert'), false);
  assert.equal(document.body.style.overflow, '');
});

test('pending Add Admin retains keyboard focus and restores disabled opener after successful refresh', async () => {
  await mountAdmins(); await act(async () => calls[0].resolve([]));
  const opener = button('Add Admin'); await click(opener); await fill(); await submitTwice();
  const dialog = document.querySelector('[role=dialog]');
  await key(dialog, 'Tab'); assert.ok(dialog.contains(document.activeElement));
  await key(dialog, 'Escape'); assert.ok(document.querySelector('[role=dialog]'));
  await act(async () => calls[1].resolve({ name: 'Admin' }));
  assert.equal(document.querySelector('[role=dialog]'), null);
  await act(async () => calls[2].resolve([]));
  assert.equal(document.activeElement === opener, true);
});

test('Admin rows/header have four matching grid columns inside an overflow region; dialogs constrain height', async () => {
  await mountAdmins(); await act(async () => calls[0].resolve([{ id: 1, name: 'Admin', email: 'long@example.test', status: 'ACTIVE' }]));
  const region = host.querySelector('[role=region][aria-label="Admin accounts"]');
  assert.ok(region); assert.equal(region.tabIndex, 0);
  const card = region.firstElementChild;
  assert.equal(card.children[0].children.length, 4); assert.equal(card.children[1].children.length, 4);
  const css = await readFile(new URL('../src/pages/superadmin/styles/Admins.module.css', import.meta.url), 'utf8');
  const columns = [...css.matchAll(/grid-template-columns: ([^;]+);/g)].map(match => match[1]);
  assert.equal(columns.length, 2); assert.equal(columns[0], columns[1]);
  assert.equal(columns[0].match(/minmax\([^)]*\)|\d+px/g).length, 4);
  assert.match(css, /overflow-x: auto/); assert.match(css, /min-width: 720px/);
  const dialogCss = await readFile(new URL('../src/components/shared/Dialog.module.css', import.meta.url), 'utf8');
  assert.match(dialogCss, /max-height: calc\(100dvh - 2rem\)/); assert.match(dialogCss, /overflow-y: auto/);
});

test('Super Admin password modal exposes dialog focus behavior without submitting password changes', async () => {
  const PasswordModal = (await server.ssrLoadModule('/src/components/shared/ChangePasswordModal.jsx')).default;
  let closed = 0;
  await act(async () => root.render(React.createElement(MemoryRouter, {},
    React.createElement(AuthContext.Provider, { value: { logout() {} } },
      React.createElement(ToastContext.Provider, { value: { showToast() {} } },
        React.createElement(PasswordModal, { accessible: true, isOpen: true, onClose: () => closed++ }))))));
  const dialog = document.querySelector('[role=dialog]'); assert.ok(dialog);
  assert.equal(document.activeElement.id, 'current-password');
  await key(document.activeElement, 'Escape'); assert.equal(closed, 1);
  assert.equal(calls.length, 0);
});


const dashboardStats = { totalUsers: 321, totalAdmins: 4, totalStudents: 300, totalProfessors: 16, activeUsers: 310, inactiveUsers: 11 };
const recentRows = [{ id: 1, userName: 'Test Actor', action: 'Recent account action', createdAt: '2026-01-01T12:00:00' }];
const resource = suffix => calls.filter(call => call.config.url.endsWith(suffix)).at(-1);
const safeFailure = { response: { status: 500, data: { message: 'private SQL and stack trace' } } };
async function mountDashboard(Component, props = {}) {
  const Dashboard = Component || (await server.ssrLoadModule('/src/pages/superadmin/components/SuperAdminDashboard.jsx')).default;
  await act(async () => root.render(React.createElement(MemoryRouter, {},
    React.createElement(AuthContext.Provider, { value: { role: 'superadmin', user: { name: 'Super Admin' }, logout() {} } },
      React.createElement(Dashboard, props)))));
}
function assertDashboardShell() {
  assert.ok(host.querySelector('aside')); assert.ok(host.querySelector('header'));
  assert.ok(host.textContent.includes('Platform Overview'));
  assert.equal(host.textContent.includes('private SQL'), false);
}

for (const [statsSucceed, logsSucceed] of [[true, true], [true, false], [false, true], [false, false]]) {
  test('dashboard independent results: statistics ' + (statsSucceed ? 'success' : 'failure') + ', activity ' + (logsSucceed ? 'success' : 'failure'), async () => {
    await mountDashboard(); assert.equal(calls.length, 2);
    await act(async () => {
      if (statsSucceed) resource('/dashboard').resolve(dashboardStats); else resource('/dashboard').reject(safeFailure);
      if (logsSucceed) resource('/activity-logs').resolve({ content: recentRows }); else resource('/activity-logs').reject(safeFailure);
    });
    assertDashboardShell();
    assert.equal(host.textContent.includes('321'), statsSucceed);
    assert.equal(host.textContent.includes('Recent account action'), logsSucceed);
    assert.equal(Boolean(button('Retry statistics')), !statsSucceed);
    assert.equal(Boolean(button('Retry activity logs')), !logsSucceed);
  });
}

for (const first of ['statistics', 'activity']) {
  test('loaded ' + first + ' remains visible while the other dashboard section is loading', async () => {
    await mountDashboard();
    await act(async () => first === 'statistics' ? resource('/dashboard').resolve(dashboardStats) : resource('/activity-logs').resolve({ content: recentRows }));
    assertDashboardShell();
    assert.ok(host.textContent.includes(first === 'statistics' ? '321' : 'Recent account action'));
    assert.ok(host.textContent.includes(first === 'statistics' ? 'Loading activity...' : 'Loading statistics...'));
  });
}

for (const failing of ['statistics', 'activity']) {
  test('retry ' + failing + ' reloads only that resource and keeps the successful dashboard section visible', async () => {
    await mountDashboard();
    const failingPath = failing === 'statistics' ? '/dashboard' : '/activity-logs';
    const goodPath = failing === 'statistics' ? '/activity-logs' : '/dashboard';
    await act(async () => {
      resource(failingPath).reject(safeFailure);
      resource(goodPath).resolve(failing === 'statistics' ? { content: recentRows } : dashboardStats);
    });
    await click(button(failing === 'statistics' ? 'Retry statistics' : 'Retry activity logs'));
    assert.equal(calls.length, 3); assert.equal(calls[2].config.url.endsWith(failingPath), true);
    assert.ok(host.textContent.includes(failing === 'statistics' ? 'Recent account action' : '321'));
    await act(async () => calls[2].resolve(failing === 'statistics' ? dashboardStats : { content: recentRows }));
    assertDashboardShell(); assert.ok(host.textContent.includes('321')); assert.ok(host.textContent.includes('Recent account action'));
  });
}

test('malformed activity page becomes a section error rather than a dashboard render crash; empty content stays empty', async () => {
  await mountDashboard();
  await act(async () => {
    resource('/dashboard').resolve(dashboardStats);
    resource('/activity-logs').resolve([]); // Older backend contract must not be silently treated as an empty page.
  });
  assertDashboardShell(); assert.ok(button('Retry activity logs')); assert.ok(host.textContent.includes('321'));
  await click(button('Retry activity logs'));
  await act(async () => resource('/activity-logs').resolve({ content: [] }));
  assert.ok(host.textContent.includes('No recent activity yet.')); assert.equal(button('Retry activity logs'), undefined);
});

test('undefined, null, object and malformed rows cannot crash activity rendering or conceal statistics', async () => {
  const { DashboardSections } = await server.ssrLoadModule('/src/pages/superadmin/components/SuperAdminDashboard.jsx');
  const ready = { isLoading: false, error: null, reload() {} };
  for (const data of [undefined, null, {}, { content: recentRows }, [null], [{ ...recentRows[0], action: {} }], [{ ...recentRows[0], createdAt: 'bad date' }]]) {
    await mountDashboard(DashboardSections, { statistics: { ...ready, data: dashboardStats }, logs: { ...ready, data } });
    assertDashboardShell(); assert.ok(button('Retry activity logs')); assert.ok(host.textContent.includes('321'));
  }
});

test('malformed statistics cannot render objects as React children or conceal valid activity', async () => {
  const { DashboardSections } = await server.ssrLoadModule('/src/pages/superadmin/components/SuperAdminDashboard.jsx');
  const ready = { isLoading: false, error: null, reload() {} };
  for (const data of [undefined, null, {}, [], { ...dashboardStats, totalUsers: {} }, { ...dashboardStats, totalUsers: -1 }]) {
    await mountDashboard(DashboardSections, { statistics: { ...ready, data }, logs: { ...ready, data: recentRows } });
    assertDashboardShell(); assert.ok(button('Retry statistics')); assert.ok(host.textContent.includes('Recent account action'));
  }
});
