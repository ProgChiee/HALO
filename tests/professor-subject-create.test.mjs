import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';

let dom, server, createRoot, Router, AuthContext, ToastContext, Subjects, api;
before(async () => {
  dom = new JSDOM('<html><body></body></html>', { url: 'http://localhost' });
  for (const key of ['window', 'document', 'HTMLElement', 'Event', 'localStorage', 'sessionStorage']) globalThis[key] = dom.window[key];
  globalThis.IS_REACT_ACT_ENVIRONMENT = true;
  ({ createRoot } = await import('react-dom/client'));
  Router = await import('react-router-dom');
  server = await createServer({ server: { middlewareMode: true }, appType: 'custom' });
  ({ AuthContext } = await server.ssrLoadModule('/src/context/login/useAuth.js'));
  ({ ToastContext } = await server.ssrLoadModule('/src/context/notifications/useToast.js'));
  Subjects = (await server.ssrLoadModule('/src/pages/professor/components/SubjectManagement.jsx')).default;
  api = (await server.ssrLoadModule('/src/services/apiClient.js')).default;
});
after(async () => { await server?.close(); dom?.window.close(); });

test('empty subject list retains Create Subject and reloads after creating without a client owner', async () => {
  const host = document.createElement('div');
  document.body.append(host);
  const root = createRoot(host);
  const originalAdapter = api.defaults.adapter;
  let records = [];
  const calls = [];
  api.defaults.adapter = async config => {
    calls.push({ method: config.method, url: config.url });
    assert.equal(config.url, '/professor/subjects');
    if (config.method === 'post') {
      const payload = JSON.parse(config.data);
      assert.deepEqual(payload, { subjectCode: 'TEST-CREATE', subjectName: 'Test subject', description: '', yearLevel: 'FIRST_YEAR' });
      records = [{ id: 100, ...payload }];
    }
    return { status: 200, headers: {}, config, data: config.method === 'post' ? records[0] : records };
  };
  try {
    await act(async () => root.render(React.createElement(AuthContext.Provider, { value: { role: 'professor', user: { name: 'Test Professor' }, logout() {} } },
      React.createElement(ToastContext.Provider, { value: { showToast() {} } },
        React.createElement(Router.MemoryRouter, { initialEntries: ['/professor/subjects'] }, React.createElement(Subjects))))));
    assert.match(host.textContent, /No subjects yet/);
    const create = host.querySelector('header button');
    assert.equal(create.textContent.trim(), 'Create Subject');
    assert.equal(create.disabled, false);
    await act(async () => create.click());
    const inputs = document.querySelectorAll('[role="dialog"] form input');
    for (const [index, value] of [[0, 'TEST-CREATE'], [1, 'Test subject']]) {
      await act(async () => {
        Object.getOwnPropertyDescriptor(dom.window.HTMLInputElement.prototype, 'value').set.call(inputs[index], value);
        inputs[index].dispatchEvent(new dom.window.Event('input', { bubbles: true }));
      });
    }
    await act(async () => document.querySelector('[role="dialog"] form').dispatchEvent(new dom.window.Event('submit', { bubbles: true, cancelable: true })));
    assert.equal(document.querySelector('[role="dialog"] form'), null);
    assert.doesNotMatch(host.textContent, /No subjects yet/);
    assert.match(host.textContent, /Test subject/);
    assert.deepEqual(calls.map(call => call.method), ['get', 'post', 'get']);
    assert.equal(host.querySelector('header button').textContent.trim(), 'Create Subject');
  } finally {
    api.defaults.adapter = originalAdapter;
    await act(async () => root.unmount());
    host.remove();
  }
});

test('archive and restore move the same subject between filters after confirmation', async () => {
  const host = document.createElement('div');
  document.body.append(host);
  const root = createRoot(host);
  const adapter = api.defaults.adapter;
  const changes = [];
  let archivedAt = null;
  api.defaults.adapter = async config => {
    if (config.method === 'put') {
      changes.push(config.url);
      archivedAt = config.url.endsWith('/archive') ? '2026-11-25T00:00:00' : null;
    }
    return { status: 200, headers: {}, config, data: [{ id: 8, subjectName: 'Archive fixture', archivedAt }] };
  };
  const click = async (container, label) => act(async () =>
    [...container.querySelectorAll('button')].find(button => button.textContent.trim() === label).click());
  try {
    await act(async () => root.render(React.createElement(AuthContext.Provider, { value: { user: { name: 'Professor' }, logout() {} } },
      React.createElement(ToastContext.Provider, { value: { showToast() {} } }, React.createElement(Router.MemoryRouter, {}, React.createElement(Subjects))))));
    await click(host, 'Archive');
    assert.deepEqual(changes, []);
    await click(document.querySelector('[role="dialog"]'), 'Archive');
    assert.doesNotMatch(host.textContent, /Archive fixture/);
    const active = host.querySelector('[role="tab"][aria-selected="true"]');
    const panel = host.querySelector('[role="tabpanel"]');
    assert.equal(panel.contains(active), false);
    await act(async () => active.dispatchEvent(new dom.window.KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true })));
    assert.equal(document.activeElement.textContent, 'Archived Subjects');
    assert.equal(document.activeElement.getAttribute('aria-selected'), 'true');
    assert.equal(host.querySelector('[aria-label="Edit subject"]').disabled, true);
    await click(host, 'Restore');
    await click(document.querySelector('[role="dialog"]'), 'Restore');
    assert.match(host.textContent, /No archived subjects/);
    assert.deepEqual(changes, ['/professor/subjects/8/archive', '/professor/subjects/8/restore']);
  } finally { await act(async () => root.unmount()); host.remove(); api.defaults.adapter = adapter; }
});

test('subject delete confirmation is truthful and conflict leaves the subject visible', async () => {
  const host = document.createElement('div'); document.body.append(host); const root = createRoot(host);
  const adapter = api.defaults.adapter, toasts = [];
  api.defaults.adapter = async config => {
    if (config.method === 'delete') throw { response: { status: 409, data: { code: 'SUBJECT_HAS_CONTENT' } } };
    return { status: 200, headers: {}, config, data: [{ id: 1, subjectCode: 'S', subjectName: 'Subject with content', yearLevel: 'FIRST_YEAR' }] };
  };
  try {
    await act(async () => root.render(React.createElement(AuthContext.Provider, { value: { user: { name: 'Professor' }, logout() {} } },
      React.createElement(ToastContext.Provider, { value: { showToast: (...args) => toasts.push(args) } }, React.createElement(Router.MemoryRouter, {}, React.createElement(Subjects))))));
    await act(async () => host.querySelector('[aria-label="Delete subject"]').click());
    assert.match(document.querySelector('[role="dialog"]').textContent, /empty subject with no enrollment history/);
    assert.doesNotMatch(host.textContent, /also remove all modules/);
    await act(async () => [...document.querySelectorAll('[role="dialog"] button')].find(button => button.textContent === 'Delete').click());
    assert.deepEqual(toasts, [['This subject has protected academic records. Archive it to close enrollment.', 'error']]);
    assert.ok(host.querySelector('[aria-label="Delete subject"]'));
    assert.ok([...document.querySelectorAll('[role="dialog"] button')].find(button => button.textContent === 'Delete'));
  } finally { await act(async () => root.unmount()); host.remove(); api.defaults.adapter = adapter; }
});

test('subject pages filter before slicing, reset between tabs and clamp after deleting the final card', async () => {
  const host = document.createElement('div');
  document.body.append(host);
  const root = createRoot(host);
  const adapter = api.defaults.adapter;
  let rows = Array.from({ length: 12 }, (_, i) => ({
    id: i + 1, subjectCode: 'S' + i, subjectName: 'Subject ' + (i + 1), archivedAt: i % 2 ? '2026-11-25' : null
  }));
  api.defaults.adapter = async config => {
    if (config.method === 'delete') rows = rows.filter(row => config.url !== '/professor/subjects/' + row.id);
    return { status: 200, headers: {}, config, data: rows };
  };
  const click = async button => act(async () => button.click());
  const button = label => [...host.querySelectorAll('button')].find(el => el.textContent.trim() === label);
  const titles = () => [...host.querySelectorAll('[aria-expanded]')].filter(el => el.closest('[role="tabpanel"]')).map(el => el.textContent);
  try {
    await act(async () => root.render(React.createElement(AuthContext.Provider, { value: { user: { name: 'Professor' }, logout() {} } },
      React.createElement(ToastContext.Provider, { value: { showToast() {} } }, React.createElement(Router.MemoryRouter, {}, React.createElement(Subjects))))));
    assert.equal(titles().length, 5);
    assert.equal(button('Previous').disabled, true);
    await click(button('Next'));
    assert.match(titles()[0], /Subject 11$/);
    assert.equal(button('Next').disabled, true);
    await click(button('Archived Subjects'));
    assert.equal(titles().length, 5);
    assert.match(titles()[0], /Subject 2$/);
    await click(host.querySelector('[aria-label="Page 2"]'));
    assert.match(titles()[0], /Subject 12$/);
    await click(host.querySelector('[aria-label="Delete subject"]'));
    await click([...document.querySelectorAll('[role="dialog"] button')].find(el => el.textContent === 'Delete'));
    assert.equal(titles().length, 5);
    assert.equal(button('Next'), undefined);
    assert.match(host.textContent, /Showing 1–5 of 5 subjects/);
  } finally { await act(async () => root.unmount()); host.remove(); api.defaults.adapter = adapter; }
});
