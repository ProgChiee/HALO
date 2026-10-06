import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';

let dom, server, createRoot, Router, AuthContext, ToastContext, Shell, pages;
let width = 1440;
const listeners = new Set();
before(async () => {
  dom = new JSDOM('<html><body></body></html>', { url: 'http://localhost' });
  for (const key of ['window', 'document', 'HTMLElement', 'Event', 'KeyboardEvent', 'localStorage', 'sessionStorage']) globalThis[key] = dom.window[key];
  globalThis.IS_REACT_ACT_ENVIRONMENT = true;
  window.matchMedia = () => ({ get matches() { return width <= 1024; }, addEventListener: (_, fn) => listeners.add(fn), removeEventListener: (_, fn) => listeners.delete(fn) });
  HTMLElement.prototype.scrollIntoView = () => {};
  ({ createRoot } = await import('react-dom/client'));
  Router = await import('react-router-dom');
  server = await createServer({ server: { middlewareMode: true }, appType: 'custom' });
  ({ AuthContext } = await server.ssrLoadModule('/src/context/login/useAuth.js'));
  ({ ToastContext } = await server.ssrLoadModule('/src/context/notifications/useToast.js'));
  const api = (await server.ssrLoadModule('/src/services/apiClient.js')).default;
  api.defaults.adapter = () => new Promise(() => {});
  Shell = (await server.ssrLoadModule('/src/pages/student/components/StudentPageShell.jsx')).default;
  pages = await Promise.all(['StudentDashboard', 'Subjects', 'LessonChat', 'Quiz', 'Progress', 'Badges', 'Profile'].map(async name => (await server.ssrLoadModule(`/src/pages/student/components/${name}.jsx`)).default));
});
after(async () => { await server.close(); dom.window.close(); });

async function mount(Component, route = '/student', logout = () => {}) {
  const host = document.createElement('div'); document.body.append(host);
  const root = createRoot(host);
  function Location() { return React.createElement('output', {}, Router.useLocation().pathname); }
  await act(async () => root.render(React.createElement(AuthContext.Provider, { value: { user: { name: 'Student' }, logout } },
    React.createElement(ToastContext.Provider, { value: { showToast() {} } },
      React.createElement(Router.MemoryRouter, { initialEntries: [route] },
        React.createElement(Router.Routes, {}, React.createElement(Router.Route, { path: '*', element: React.createElement(Component) })), React.createElement(Location))))));
  return async () => { await act(async () => root.unmount()); host.remove(); };
}
const click = async element => act(async () => element.click());

test('all seven Student pages retain navigation during loading at desktop, tablet and mobile breakpoints', async () => {
  for (width of [1440, 768, 390]) {
    for (const Page of pages) {
      const cleanup = await mount(Page);
      try {
        const toggle = document.querySelector('[aria-label="Open navigation"]');
        if (width > 1024) { assert.equal(toggle, null); assert.equal(document.querySelectorAll('nav a').length, 5); }
        else {
          assert.ok(toggle); assert.equal(document.querySelector('aside'), null);
          await click(toggle);
          assert.equal(toggle.getAttribute('aria-expanded'), 'true');
          assert.equal(document.querySelectorAll('[role="dialog"] nav a').length, 5);
          await click(document.querySelector('[aria-label="Close navigation"]'));
          assert.equal(document.querySelector('[role="dialog"]'), null);
          assert.equal(document.activeElement, toggle);
        }
      } finally { await cleanup(); }
    }
  }
});

test('compact navigation preserves links, active state, Escape, desktop transition and logout', async () => {
  width = 390; let logouts = 0;
  const cleanup = await mount(Shell, '/student/subjects', () => { logouts++; });
  try {
    let toggle = document.querySelector('[aria-label="Open navigation"]');
    await click(toggle);
    assert.equal(document.querySelector('nav a[aria-current="page"]').getAttribute('href'), '/student/subjects');
    assert.deepEqual([...document.querySelectorAll('nav a')].map(a => a.getAttribute('href')), ['/student', '/student/subjects', '/student/progress', '/student/badges', '/student/profile']);
    await act(async () => document.querySelector('[role="dialog"]').dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })));
    assert.equal(document.querySelector('[role="dialog"]'), null);
    assert.equal(document.activeElement, toggle);
    await click(toggle);
    await click(document.querySelector('a[href="/student/progress"]'));
    assert.equal(document.querySelector('output').textContent, '/student/progress');
    assert.equal(document.querySelector('[role="dialog"]'), null);
    await click(toggle);
    await act(async () => { width = 1440; listeners.forEach(fn => fn({ matches: false })); });
    assert.equal(document.querySelector('[role="dialog"]'), null);
    assert.ok(document.querySelector('aside'));
    await act(async () => { width = 768; listeners.forEach(fn => fn({ matches: true })); });
    toggle = document.querySelector('[aria-label="Open navigation"]');
    assert.equal(toggle.getAttribute('aria-expanded'), 'false');
    await click(toggle);
    await click(document.querySelector('[aria-label="Log out"]'));
    assert.equal(logouts, 1);
    assert.equal(document.querySelector('output').textContent, '/login');
    assert.equal(document.querySelector('[role="dialog"]'), null);
    assert.equal(document.querySelector('[inert]'), null);
  } finally { await cleanup(); }
});
