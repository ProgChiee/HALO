import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import React, { act } from 'react';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
let dom, server, createRoot, PasswordInput, Modal, AuthContext, ToastContext, MemoryRouter;
before(async () => {
  dom = new JSDOM('<html><body></body></html>', { url: 'http://localhost' });
  for (const key of ['window','document','HTMLElement','Event','MouseEvent','KeyboardEvent','localStorage','sessionStorage']) globalThis[key] = dom.window[key];
  globalThis.IS_REACT_ACT_ENVIRONMENT = true;
  ({ createRoot } = await import('react-dom/client')); ({ MemoryRouter } = await import('react-router-dom'));
  server = await createServer({ server: { middlewareMode: true, hmr: false }, appType: 'custom' });
  PasswordInput = (await server.ssrLoadModule('/src/components/shared/PasswordInput.jsx')).default;
  Modal = (await server.ssrLoadModule('/src/components/shared/ChangePasswordModal.jsx')).default;
  ({ AuthContext } = await server.ssrLoadModule('/src/context/login/useAuth.js'));
  ({ ToastContext } = await server.ssrLoadModule('/src/context/notifications/useToast.js'));
});
after(async () => { await server.close(); dom.window.close(); });
test('fields toggle independently without changing values, autocomplete, validation or submitting', async () => {
  const host = document.createElement('div'); document.body.append(host); const root = createRoot(host); let submitted = 0;
  const render = (value = 'Synthetic-password', disabled = false) => root.render(React.createElement('form', { onSubmit: e => { e.preventDefault(); submitted++; } },
    React.createElement(PasswordInput, { id: 'one', name: 'password', value, onChange() {}, required: true, minLength: 8, autoComplete: 'new-password', disabled }),
    React.createElement(PasswordInput, { id: 'two', value: 'Confirmation', onChange() {}, autoComplete: 'new-password' }),
    React.createElement('button', { type: 'submit' }, 'Submit')));
  try {
    await act(async () => render());
    const inputs = host.querySelectorAll('input'), buttons = host.querySelectorAll('button');
    assert.deepEqual([...inputs].map(i => i.type), ['password','password']);
    assert.equal(buttons[0].type, 'button'); assert.equal(buttons[0].getAttribute('aria-controls'), 'one');
    buttons[0].focus(); assert.equal(document.activeElement, buttons[0]);
    await act(async () => buttons[0].click());
    assert.equal(inputs[0].type, 'text'); assert.equal(inputs[1].type, 'password');
    assert.equal(inputs[0].value, 'Synthetic-password'); assert.equal(inputs[0].autocomplete, 'new-password');
    assert.equal(inputs[0].minLength, 8); assert.equal(inputs[0].required, true); assert.equal(submitted, 0);
    assert.equal(buttons[0].getAttribute('aria-label'), 'Hide password');
    await act(async () => buttons[1].click()); await act(async () => buttons[0].click());
    assert.equal(inputs[0].type, 'password'); assert.equal(inputs[1].type, 'text');
    await act(async () => host.querySelector('form').reset()); assert.equal(inputs[1].type, 'password');
    await act(async () => buttons[0].click()); await act(async () => render(''));
    assert.equal(inputs[0].type, 'password'); assert.equal(inputs[0].value, '');
    await act(async () => render('Synthetic-password', true)); assert.equal(buttons[0].disabled, true);
    await act(async () => render()); await act(async () => buttons[2].click()); assert.equal(submitted, 1);
  } finally { await act(async () => root.unmount()); host.remove(); }
});
test('Change Password retains validation and resets all visibility on modal reopening', async () => {
  const host = document.createElement('div'); document.body.append(host); const root = createRoot(host);
  const render = isOpen => root.render(React.createElement(MemoryRouter, null,
    React.createElement(AuthContext.Provider, { value: { logout() {} } },
      React.createElement(ToastContext.Provider, { value: { showToast() {} } }, React.createElement(Modal, { isOpen, onClose() {} })))));
  try {
    await act(async () => render(true));
    const dialog = () => document.querySelector('[role="dialog"]');
    const fields = dialog().querySelectorAll('input'); assert.equal(fields.length, 3);
    for (const button of dialog().querySelectorAll('[aria-label="Show password"]')) await act(async () => button.click());
    assert.ok([...fields].every(input => input.type === 'text'));
    await act(async () => dialog().querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })));
    assert.match(dialog().textContent, /Please fill in all fields/);
    await act(async () => render(false)); await act(async () => render(true));
    assert.ok([...dialog().querySelectorAll('input')].every(input => input.type === 'password'));
  } finally { await act(async () => root.unmount()); host.remove(); }
});
test('all existing password forms use the shared control', async () => {
  const forms = ['components/shared/ChangePasswordModal.jsx','pages/login/components/Login.jsx','pages/login/components/Register.jsx','pages/login/components/ForgotPassword.jsx','pages/admin/components/ProfessorManagement.jsx','pages/superadmin/components/Admins.jsx'];
  for (const path of forms) {
    const source = await readFile(new URL('../src/'+path, import.meta.url), 'utf8');
    assert.match(source, /<PasswordInput/); assert.doesNotMatch(source, /showConfirmPassword|showPassword|type="password"/);
  }
});
