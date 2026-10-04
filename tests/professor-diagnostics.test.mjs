import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';
import { logProfessorError } from '../src/utils/professorDiagnostics.js';

const secret = 'SECRET_TOKEN_PASSWORD_PERSONAL_DATA';
const failure = { message: secret, config: { headers: { Authorization: secret }, data: { password: secret } }, response: { status: 403, data: { code: 'ACCESS_DENIED', message: secret, user: secret } } };
test('diagnostics only contain fixed labels, numeric statuses and allowlisted codes', () => {
  const original = console.error, calls = [];
  console.error = (...args) => calls.push(args);
  try {
    logProfessorError('load-dashboard', failure);
    assert.deepEqual(calls[0], ['[HALO Professor]', { operation: 'load-dashboard', status: 403, code: 'ACCESS_DENIED' }]);
    logProfessorError(secret, { response: { status: secret, data: { code: secret } } });
    logProfessorError('load-profile', null);
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
  for (const name of ['ProfessorDashboard', 'ProfessorProfile']) {
    const Component = (await server.ssrLoadModule(`/src/pages/professor/components/${name}.jsx`)).default;
    const host = document.createElement('div'); document.body.append(host); const root = createRoot(host);
    const originalError = console.error, originalWarn = console.warn, adapter = api.defaults.adapter, calls = [];
    console.error = (...args) => calls.push(args);
    console.warn = (...args) => calls.push(args);
    api.defaults.adapter = async () => { throw failure; };
    try {
      await act(async () => root.render(React.createElement(AuthContext.Provider, { value: { user: { name: 'Professor' }, logout() {} } },
        React.createElement(ToastContext.Provider, { value: { showToast() {} } }, React.createElement(Router.MemoryRouter, {}, React.createElement(Component))))));
      assert.match(host.textContent, /couldn't load|could not load|unable to load|failed to load/i);
      assert.ok(calls.some(call => call[0] === '[HALO Professor]' && call[1].status === 403));
      assert.ok(!JSON.stringify(calls).includes(secret));
    } finally {
      await act(async () => root.unmount()); host.remove(); console.error = originalError; console.warn = originalWarn; api.defaults.adapter = adapter;
    }
  }
});

test('Professor dashboard renders scoped counts and labels the global account count', async () => {
  const Component = (await server.ssrLoadModule('/src/pages/professor/components/ProfessorDashboard.jsx')).default;
  const host = document.createElement('div'); document.body.append(host); const root = createRoot(host);
  const adapter = api.defaults.adapter;
  api.defaults.adapter = async config => ({ status: 200, headers: {}, config, data: {
    totalStudents: 12, totalSubjects: 2, totalModules: 3, approvedModules: 1, totalAssessments: 4, totalPassedAttempts: 5,
  } });
  try {
    await act(async () => root.render(React.createElement(AuthContext.Provider, { value: { user: { name: 'Professor' }, logout() {} } },
      React.createElement(Router.MemoryRouter, {}, React.createElement(Component)))));
    for (const [label, value] of [['Institution-wide Student Accounts', '12'], ['Subjects', '2'], ['Total Modules', '3'], ['Approved Modules', '1'], ['Assessments', '4'], ['Passed Attempts', '5']]) {
      const labelNode = [...host.querySelectorAll('p')].find(node => node.textContent === label);
      assert.ok(labelNode, label);
      assert.equal(labelNode.previousElementSibling.textContent, value);
    }
  } finally { await act(async () => root.unmount()); host.remove(); api.defaults.adapter = adapter; }
});
