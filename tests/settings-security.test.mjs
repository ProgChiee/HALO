import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';

let dom, server, createRoot, Router, AuthContext, ToastContext, Settings, api, ProfessorContext, StudentContext;
const h = React.createElement;
const roles = ['student', 'professor', 'admin', 'superadmin'];
before(async () => {
  dom = new JSDOM('<html><body></body></html>', { url: 'http://localhost' });
  for (const key of ['window', 'document', 'HTMLElement', 'Event', 'KeyboardEvent', 'localStorage', 'sessionStorage']) globalThis[key] = dom.window[key];
  globalThis.IS_REACT_ACT_ENVIRONMENT = true;
  ({ createRoot } = await import('react-dom/client'));
  Router = await import('react-router-dom');
  server = await createServer({ server: { middlewareMode: true }, appType: 'custom' });
  const load = path => server.ssrLoadModule('/src/' + path);
  Settings = (await load('components/shared/Settings.jsx')).default;
  ({ AuthContext } = await load('context/login/useAuth.js'));
  ({ ToastContext } = await load('context/notifications/useToast.js'));
  ({ ProfessorWorkflowContext: ProfessorContext } = await load('context/professor/useProfessorWorkflow.js'));
  ({ StudentWorkflowContext: StudentContext } = await load('context/student/useStudentWorkflow.js'));
  api = (await load('services/apiClient.js')).default;
});
after(async () => { await server?.close(); dom?.window.close(); });
const button = (node, text) => [...node.querySelectorAll('button')].find(b => b.textContent.trim() === text);
const click = async node => act(async () => node.click());
async function mount(role, { Component = Settings, mustChangePassword = false, context, workflow } = {}) {
  const host = document.createElement('div'); document.body.append(host);
  const root = createRoot(host), feedback = [];
  let logoutCount = 0;
  function Location() { return h('output', { id: 'location' }, Router.useLocation().pathname); }
  let content = h(Component, { role });
  if (context) content = h(context.Provider, { value: workflow }, content);
  await act(async () => root.render(h(AuthContext.Provider, { value: { role, user: { name: 'Authenticated owner', mustChangePassword }, logout: () => logoutCount++ } },
    h(ToastContext.Provider, { value: { showToast: (...args) => feedback.push(args) } },
      h(Router.MemoryRouter, { initialEntries: [`/${role}/settings`] }, content, h(Location))))));
  return { host, feedback, get logoutCount() { return logoutCount; }, async close() { await act(async () => root.unmount()); host.remove(); } };
}
async function fill() {
  for (const [id, value] of [['current-password', 'Original-test-123'], ['new-password', 'Replacement-test-123'], ['confirm-password', 'Replacement-test-123']]) {
    await act(async () => {
      const input = document.getElementById(id);
      Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set.call(input, value);
      input.dispatchEvent(new Event('input', { bubbles: true }));
    });
  }
}
async function submit() { await act(async () => document.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))); }

test('all roles open the existing Settings modal; background, Escape and outside clicks cannot dismiss it', async () => {
  for (const role of roles) {
    let themes = 0, requests = 0;
    window.haloTheme = { get: () => 'light', set: () => themes++ };
    api.defaults.adapter = async () => { requests++; throw Error('No request expected'); };
    const ui = await mount(role);
    try {
      assert.match(ui.host.textContent, /Account & Security/);
      const opener = button(ui.host, 'Change password');
      await click(opener);
      const dialog = document.querySelector('[role="dialog"]');
      assert.equal(document.activeElement.id, 'current-password');
      assert.equal(dialog.getAttribute('aria-modal'), 'true');
      await click(dialog.parentElement);
      await click(ui.host.querySelector('input[value="dark"]'));
      await act(async () => dialog.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })));
      assert.ok(dialog.isConnected); assert.equal(themes, 0);
      await act(async () => opener.focus());
      assert.ok(dialog.contains(document.activeElement));
      await click(button(dialog, 'Cancel'));
      assert.equal(document.querySelector('[role="dialog"]'), null);
      assert.equal(document.activeElement, opener);
      await click(ui.host.querySelector('input[value="dark"]'));
      assert.equal(themes, 1); assert.equal(requests, 0);
    } finally { await ui.close(); }
  }
});

test('each role retains validation, pending lock, failure retry and self-only password submission from Settings', async () => {
  for (const role of roles) {
    const calls = []; let resolve, reject;
    api.defaults.adapter = config => { calls.push(config); return new Promise((yes, no) => { resolve = yes; reject = no; }); };
    const ui = await mount(role);
    try {
      await click(button(ui.host, 'Change password'));
      await submit();
      assert.match(document.querySelector('[role="dialog"]').textContent, /Please fill in all fields/);
      assert.equal(calls.length, 0);
      await fill(); await submit(); await submit();
      assert.equal(calls.length, 1);
      assert.ok(button(document, 'Cancel').disabled);
      assert.ok(document.querySelector('[aria-label="Close"]').disabled);
      assert.equal(calls[0].url, '/auth/change-password');
      assert.deepEqual(JSON.parse(calls[0].data), { currentPassword: 'Original-test-123', newPassword: 'Replacement-test-123' });
      assert.equal(calls[0].params, undefined);
      await act(async () => reject({ response: { status: 400, data: { code: 'INVALID_CURRENT_PASSWORD' } } }));
      assert.ok(document.querySelector('[role="dialog"]'));
      assert.equal(document.getElementById('new-password').value, 'Replacement-test-123');
      assert.equal(ui.logoutCount, 0);
      await submit();
      await act(async () => resolve({ status: 200, headers: {}, config: calls[1], data: {} }));
      assert.equal(ui.logoutCount, 1);
      assert.deepEqual(ui.feedback[0], ['Password changed. Please sign in again.', 'success']);
      assert.equal(ui.host.querySelector('#location').textContent, '/login');
      assert.equal(document.querySelector('[role="dialog"]'), null);
    } finally { await ui.close(); }
  }
});

test('all four Profile pages retain identity details without password cards or dialogs', async () => {
  for (const [role, name] of [['admin', 'AdminProfile'], ['superadmin', 'SuperAdminProfile'], ['professor', 'ProfessorProfile'], ['student', 'Profile']]) {
    api.defaults.adapter = async config => ({ status: 200, headers: {}, config, data: config.url.includes('photo') ? null : { userId: 1, name: 'Authenticated owner', email: 'synthetic@example.test', status: 'ACTIVE', studentId: '29AHRT-0001', professorId: 'PROF-001', section: 'BSHM 1A', yearLevel: 'FIRST_YEAR' } });
    const Component = (await server.ssrLoadModule(`/src/pages/${role}/components/${name}.jsx`)).default;
    const ui = await mount(role, { Component });
    try {
      assert.match(ui.host.textContent, /synthetic@example.test/);
      assert.equal(button(ui.host, 'Change password'), undefined);
      assert.equal(document.querySelector('[role="dialog"]'), null);
      assert.ok(![...ui.host.querySelectorAll('h2')].some(node => node.textContent === 'Account'));
    } finally { await ui.close(); }
  }
});

test('Preview, Acting and Support contexts never expose a target password action or forced dialog', async () => {
  for (const context of [ProfessorContext, StudentContext]) for (const preview of [true, false]) {
    let requests = 0;
    api.defaults.adapter = async () => { requests++; throw Error('No request expected'); };
    const ui = await mount('admin', { context, mustChangePassword: true, workflow: { acting: true, preview, target: { userId: 99 } } });
    try {
      assert.equal(button(ui.host, 'Change password'), undefined);
      assert.equal(document.querySelector('[role="dialog"]'), null);
      assert.match(ui.host.textContent, /Appearance/);
      assert.equal(requests, 0);
    } finally { await ui.close(); }
  }
});

test('temporary-password Admin and Super Admin accounts automatically open the Settings form', async () => {
  for (const role of ['admin', 'superadmin']) {
    const ui = await mount(role, { mustChangePassword: true });
    try {
      assert.match(ui.host.textContent, /Change your temporary password/);
      assert.equal(document.activeElement.id, 'current-password');
      await click(document.querySelector('[aria-label="Close"]'));
      await click(button(ui.host, 'Change password'));
      assert.ok(document.querySelector('[role="dialog"]'));
    } finally { await ui.close(); }
  }
});
