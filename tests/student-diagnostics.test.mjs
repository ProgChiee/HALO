import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';
import { logStudentError } from '../src/utils/studentDiagnostics.js';

const secret = 'SECRET_TOKEN_PASSWORD_PERSONAL_DATA';
const failure = { message: secret, config: { headers: { Authorization: secret }, data: { password: secret, answers: [{ questionId: 42, answer: secret }] } }, response: { status: 403, data: { code: 'ACCESS_DENIED', message: secret, user: secret } } };
test('diagnostics only contain fixed labels, numeric statuses and allowlisted codes', () => {
  const original = console.error, calls = [];
  console.error = (...args) => calls.push(args);
  try {
    logStudentError('load-dashboard', failure);
    assert.deepEqual(calls[0], ['[HALO Student]', { operation: 'load-dashboard', status: 403, code: 'ACCESS_DENIED' }]);
    logStudentError(secret, { response: { status: secret, data: { code: secret } } });
    logStudentError('load-profile', null);
    assert.deepEqual(calls[1][1], { operation: 'request-failed' });
    assert.ok(!JSON.stringify(calls).includes(secret));
  } finally { console.error = original; }
});

let dom, server, createRoot, Router, AuthContext, ToastContext, api;
before(async () => {
  dom = new JSDOM('<html><body></body></html>', { url: 'http://localhost' });
  for (const key of ['window', 'document', 'HTMLElement', 'Event', 'localStorage', 'sessionStorage']) globalThis[key] = dom.window[key];
  globalThis.IS_REACT_ACT_ENVIRONMENT = true;
  ({ createRoot } = await import('react-dom/client'));
  Router = await import('react-router-dom');
  server = await createServer({ server: { middlewareMode: true }, appType: 'custom' });
  ({ AuthContext } = await server.ssrLoadModule('/src/context/login/useAuth.js'));
  ({ ToastContext } = await server.ssrLoadModule('/src/context/notifications/useToast.js'));
  api = (await server.ssrLoadModule('/src/services/apiClient.js')).default;
});
after(async () => { await server?.close(); dom?.window.close(); });

test('dashboard and profile retain error UI while logging only sanitized diagnostics', async () => {
  for (const name of ['StudentDashboard', 'Subjects', 'Quiz', 'Progress', 'Badges', 'Profile']) {
    const Component = (await server.ssrLoadModule(`/src/pages/student/components/${name}.jsx`)).default;
    const host = document.createElement('div'); document.body.append(host); const root = createRoot(host);
    const originalError = console.error, originalWarn = console.warn, adapter = api.defaults.adapter, calls = [];
    console.error = (...args) => calls.push(args);
    console.warn = (...args) => calls.push(args);
    api.defaults.adapter = async config => { throw { ...failure, config: { ...config, headers: { Authorization: secret }, data: secret } }; };
    try {
      await act(async () => root.render(React.createElement(AuthContext.Provider, { value: { user: { name: 'Student' }, logout() {} } },
        React.createElement(ToastContext.Provider, { value: { showToast() {} } }, React.createElement(Router.MemoryRouter, {}, React.createElement(Component))))));
      assert.match(host.textContent, /couldn't load|could not load|unable to load|failed to load/i);
      assert.ok(calls.some(call => call[0] === '[HALO Student]' && call[1].status === 403));
      assert.ok(!JSON.stringify(calls).includes(secret));
    } finally {
      await act(async () => root.unmount()); host.remove(); console.error = originalError; console.warn = originalWarn; api.defaults.adapter = adapter;
    }
  }
});


test('Progress uses eligible module counts instead of week totals and handles zero', async () => {
  const Component = (await server.ssrLoadModule('/src/pages/student/components/Progress.jsx')).default;
  for (const count of [2, 0]) {
    const host = document.createElement('div'); document.body.append(host); const root = createRoot(host);
    const adapter = api.defaults.adapter;
    api.defaults.adapter = async config => ({ status: 200, headers: {}, config, data: [{ subjectId: 1, subjectName: 'Subject', totalWeeks: 8, eligibleModuleCount: count, completedWeeks: count ? 1 : 0, progressPercentage: count ? 50 : 0 }] });
    try {
      await act(async () => root.render(React.createElement(AuthContext.Provider, { value: { user: { name: 'Student' }, logout() {} } }, React.createElement(Router.MemoryRouter, {}, React.createElement(Component)))));
      const label = text => [...host.querySelectorAll('p')].find(node => node.textContent === text);
      assert.equal(label('Total modules').previousElementSibling.textContent, String(count));
      assert.equal(label('Modules completed').previousElementSibling.textContent, count ? '1' : '0');
      assert.match(host.textContent, count ? /50%/ : /0%/);
      assert.doesNotMatch(host.textContent, /NaN|Infinity/);
    } finally { await act(async () => root.unmount()); host.remove(); api.defaults.adapter = adapter; }
  }
});
