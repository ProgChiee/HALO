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
    const inputs = host.querySelectorAll('form input');
    for (const [index, value] of [[0, 'TEST-CREATE'], [1, 'Test subject']]) {
      await act(async () => {
        Object.getOwnPropertyDescriptor(dom.window.HTMLInputElement.prototype, 'value').set.call(inputs[index], value);
        inputs[index].dispatchEvent(new dom.window.Event('input', { bubbles: true }));
      });
    }
    await act(async () => host.querySelector('form').dispatchEvent(new dom.window.Event('submit', { bubbles: true, cancelable: true })));
    assert.equal(host.querySelector('form'), null);
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
    assert.match(host.textContent, /Only an empty subject can be deleted/);
    assert.doesNotMatch(host.textContent, /also remove all modules/);
    await act(async () => [...host.querySelectorAll('button')].find(button => button.textContent === 'Delete').click());
    assert.deepEqual(toasts, [['This subject still contains weeks or learning modules. Remove its content before deleting the subject.', 'error']]);
    assert.ok(host.querySelector('[aria-label="Delete subject"]'));
    assert.ok([...host.querySelectorAll('button')].find(button => button.textContent === 'Delete'));
  } finally { await act(async () => root.unmount()); host.remove(); api.defaults.adapter = adapter; }
});
