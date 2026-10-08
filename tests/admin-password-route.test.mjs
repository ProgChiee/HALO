import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';
import { ROLE_HOME, getPasswordChangeRoute } from '../src/utils/roles.js';

let server, dom, createRoot, ProtectedRoute, AuthContext, ToastContext, Router, Profile, api;
before(async () => {
  dom = new JSDOM('<html><body></body></html>', { url: 'http://localhost' });
  for (const key of ['window', 'document', 'HTMLElement', 'Event', 'localStorage', 'sessionStorage']) globalThis[key] = dom.window[key];
  globalThis.IS_REACT_ACT_ENVIRONMENT = true;
  ({ createRoot } = await import('react-dom/client'));
  Router = await import('react-router-dom');
  server = await createServer({ server: { middlewareMode: true }, appType: 'custom' });
  ProtectedRoute = (await server.ssrLoadModule('/src/routes/ProtectedRoute.jsx')).default;
  ({ AuthContext } = await server.ssrLoadModule('/src/context/login/useAuth.js'));
  ({ ToastContext } = await server.ssrLoadModule('/src/context/notifications/useToast.js'));
  Profile = (await server.ssrLoadModule('/src/pages/admin/components/AdminProfile.jsx')).default;
  api = (await server.ssrLoadModule('/src/services/apiClient.js')).default;
});
after(async () => { await server.close(); dom.window.close(); });
test('login destinations preserve normal Admin and forced role-specific profiles', () => {
  assert.equal(ROLE_HOME.admin, '/admin');
  assert.equal(getPasswordChangeRoute('admin'), '/admin/profile');
  assert.equal(getPasswordChangeRoute('superadmin'), '/superadmin/profile');
});
test('guard redirects forced users to their own profile without loops or cross-role access', async () => {
  for (const role of ['admin', 'superadmin']) {
    for (const forced of [false, true]) {
      for (const initial of [`/${role}`, `/${role}/profile`]) {
        const host = document.createElement('div'); document.body.append(host); const root = createRoot(host);
        const wrap = text => React.createElement(ProtectedRoute, { allowedRoles: [role] }, text);
        try {
          await act(async () => root.render(React.createElement(AuthContext.Provider, { value: { role, user: { mustChangePassword: forced }, isAuthenticated: true } },
            React.createElement(Router.MemoryRouter, { initialEntries: [initial] }, React.createElement(Router.Routes, {},
              React.createElement(Router.Route, { path: `/${role}`, element: wrap('Business') }),
              React.createElement(Router.Route, { path: `/${role}/profile`, element: wrap('Password profile') }))))));
          assert.equal(host.textContent, forced || initial.endsWith('/profile') ? 'Password profile' : 'Business');
        } finally { await act(async () => root.unmount()); host.remove(); }
      }
    }
  }
});
test('Admin profile automatically exposes password form and keeps close/reopen behavior', async () => {
  const host = document.createElement('div'); document.body.append(host); const root = createRoot(host);
  const calls = [];
  let logoutCount = 0;
  let submitted;
  function Location() { return React.createElement('output', { id: 'location' }, Router.useLocation().pathname); }
  api.defaults.adapter = async config => { calls.push(config.url); if (config.method === 'post') submitted = JSON.parse(config.data); return { status: 200, headers: {}, config, data: { name: 'Admin', email: 'admin@example.test', status: 'ACTIVE' } }; };
  try {
    await act(async () => root.render(React.createElement(AuthContext.Provider, { value: { role: 'admin', user: { name: 'Admin', mustChangePassword: true }, logout() { logoutCount++; } } },
      React.createElement(ToastContext.Provider, { value: { showToast() {} } }, React.createElement(Router.MemoryRouter, {}, React.createElement(Profile), React.createElement(Location))))));
    assert.match(host.textContent, /Change your temporary password/);
    assert.ok(document.querySelector('#current-password'));
    assert.ok(document.querySelector('#new-password'));
    assert.deepEqual(calls, ['/admin/profile']);
    await act(async () => document.querySelector('button[aria-label="Close"]').click());
    assert.equal(document.querySelector('#current-password'), null);
    await act(async () => [...document.querySelectorAll('button')].find(button => button.textContent.trim() === 'Change password').click());
    const opener = [...host.querySelectorAll('button')].find(button => button.textContent.trim() === 'Change password');
    await act(async () => document.querySelector('[role="dialog"]').dispatchEvent(new dom.window.KeyboardEvent('keydown', {key:'Escape',bubbles:true})));
    await act(async () => { opener.focus(); opener.click(); });
    const dialog = document.querySelector('[role="dialog"]');
    assert.equal(dialog.getAttribute('aria-labelledby'), 'change-password-title');
    assert.equal(document.activeElement.id, 'current-password');
    for (let i=0;i<9;i++) await act(async () => {
      dialog.dispatchEvent(new dom.window.KeyboardEvent('keydown', {key:'Tab',bubbles:true}));
      assert.ok(dialog.contains(document.activeElement));
    });
    await act(async () => dialog.dispatchEvent(new dom.window.KeyboardEvent('keydown', {key:'Escape',bubbles:true})));
    assert.equal(document.querySelector('[role="dialog"]'),null);
    assert.equal(document.activeElement,opener);
    await act(async () => opener.click());
    for (const [id, value] of [['current-password', 'Temporary-test-123'], ['new-password', 'Replacement-test-123'], ['confirm-password', 'Replacement-test-123']]) {
      await act(async () => {
        const input = document.querySelector('#' + id);
        Object.getOwnPropertyDescriptor(dom.window.HTMLInputElement.prototype, 'value').set.call(input, value);
        input.dispatchEvent(new dom.window.Event('input', { bubbles: true }));
      });
    }
    await act(async () => document.querySelector('form').dispatchEvent(new dom.window.Event('submit', { bubbles: true, cancelable: true })));
    assert.deepEqual(submitted, { currentPassword: 'Temporary-test-123', newPassword: 'Replacement-test-123' });
    assert.equal(calls.at(-1), '/auth/change-password');
    assert.equal(logoutCount, 1);
    assert.equal(document.querySelector('#location').textContent, '/login');
    assert.equal(document.querySelector('#current-password'), null);
  } finally { await act(async () => root.unmount()); host.remove(); }
});
