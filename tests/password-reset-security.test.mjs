import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';
import { recoveryCooldown, recoveryError, recoveryRequestMessage } from '../src/utils/passwordReset.js';

let dom, server, createRoot, Router, ForgotPassword, ToastContext, api;
before(async () => {
  dom = new JSDOM('<html><body></body></html>', { url: 'http://localhost' });
  for (const key of ['window', 'document', 'HTMLElement', 'Event', 'MouseEvent', 'localStorage', 'sessionStorage']) globalThis[key] = dom.window[key];
  globalThis.requestAnimationFrame = callback => setTimeout(callback, 0);
  globalThis.IS_REACT_ACT_ENVIRONMENT = true;
  ({ createRoot } = await import('react-dom/client')); Router = await import('react-router-dom');
  server = await createServer({ server: { middlewareMode: true }, appType: 'custom' });
  ForgotPassword = (await server.ssrLoadModule('/src/pages/login/components/ForgotPassword.jsx')).default;
  ({ ToastContext } = await server.ssrLoadModule('/src/context/notifications/useToast.js'));
  api = (await server.ssrLoadModule('/src/services/apiClient.js')).default;
});
after(async () => { await server?.close(); dom?.window.close(); });
async function mount() {
  const host = document.createElement('div'); document.body.append(host); const root = createRoot(host); const toasts = [];
  await act(async () => root.render(React.createElement(Router.MemoryRouter, { initialEntries: ['/forgot-password'] },
    React.createElement(ToastContext.Provider, { value: { showToast: message => toasts.push(message) } },
      React.createElement(Router.Routes, null,
        React.createElement(Router.Route, { path: '/forgot-password', element: React.createElement(ForgotPassword) }),
        React.createElement(Router.Route, { path: '/login', element: React.createElement('p', null, 'Login page') }))))));
  return { host, toasts, submit: () => host.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })),
    close: async () => { await act(async () => root.unmount()); host.remove(); } };
}
async function fill(input, value) {
  await act(async () => { Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set.call(input, value); input.dispatchEvent(new Event('input', { bubbles: true })); });
}
const response = (config, seconds = 60) => ({ config, status: 200, headers: {}, data: { message: recoveryRequestMessage, retryAfterSeconds: seconds } });
async function requestCode(view) {
  await fill(view.host.querySelector('input'), 'student@paterostechnologicalcollege.edu.ph');
  await act(async () => view.submit());
}
async function fillReset(view) {
  const inputs = view.host.querySelectorAll('input');
  for (const [index, value] of ['123456', 'replacement-password', 'replacement-password'].entries()) await fill(inputs[index], value);
}
test('safe recovery feedback uses server cooldowns without exposing arbitrary server text', () => {
  assert.equal(recoveryCooldown(90), 90); assert.equal(recoveryCooldown(undefined), 60);
  assert.equal(recoveryCooldown(-4), 60);
  assert.equal(recoveryError({ response: { status: 429, data: { retryAfterSeconds: 125, message: 'private account detail' } } }).seconds, 125);
  assert.match(recoveryError({ response: { status: 400, data: { code: 'INVALID_RESET_REQUEST' } } }).message, /attempt limit/);
  assert.doesNotMatch(recoveryError({ response: { status: 500, data: { message: 'private account detail' } } }).message, /private/);
});
test('request is generic, repeated pending submits send once, and server cooldown prevents resend/back bypass', async () => {
  let calls = 0, resolve; api.defaults.adapter = config => { calls++; return new Promise(done => { resolve = () => done(response(config, 75)); }); };
  const view = await mount();
  try {
    await fill(view.host.querySelector('input'), 'student@paterostechnologicalcollege.edu.ph');
    await act(async () => { view.submit(); view.submit(); }); assert.equal(calls, 1);
    assert.equal(view.host.querySelector('[type="submit"]').disabled, true);
    await act(async () => resolve()); assert.equal(view.toasts[0], recoveryRequestMessage);
    const resend = [...view.host.querySelectorAll('button')].find(button => button.textContent.includes('Resend'));
    assert.equal(resend.disabled, true); assert.match(resend.textContent, /75s/);
    await act(async () => [...view.host.querySelectorAll('button')].find(button => button.textContent.trim() === 'Back').click());
    assert.equal(view.host.querySelector('[type="submit"]').disabled, true);
    await act(async () => view.submit()); assert.equal(calls, 1);
  } finally { await view.close(); }
});
test('resend rate limit stays on the form and disables recovery controls for the server wait', async () => {
  let calls = 0; api.defaults.adapter = config => ++calls === 1 ? Promise.resolve(response(config, 1)) : Promise.reject({ response: { status: 429, data: { retryAfterSeconds: 90 } } });
  const view = await mount();
  try {
    await requestCode(view); await act(async () => new Promise(resolve => setTimeout(resolve, 1100)));
    const resend = [...view.host.querySelectorAll('button')].find(button => button.textContent.includes('Resend'));
    await act(async () => resend.click()); assert.equal(calls, 2);
    assert.match(view.host.querySelector('[role="alert"]').textContent, /90 seconds/);
    assert.equal(resend.disabled, true); assert.equal(view.host.querySelector('[type="submit"]').disabled, true);
  } finally { await view.close(); }
});
test('verification throttling retains inputs and blocks duplicate requests', async () => {
  let calls = 0, reject; api.defaults.adapter = config => ++calls === 1 ? Promise.resolve(response(config)) : new Promise((_, fail) => { reject = fail; });
  const view = await mount();
  try {
    await requestCode(view); await fillReset(view);
    await act(async () => { view.submit(); view.submit(); }); assert.equal(calls, 2);
    await act(async () => reject({ response: { status: 429, data: { retryAfterSeconds: 120 } } }));
    assert.equal(view.host.querySelector('input').value, '123456');
    assert.equal(view.host.querySelector('[type="submit"]').disabled, true);
    assert.match(view.host.querySelector('[role="alert"]').textContent, /120 seconds/);
    await act(async () => view.submit()); assert.equal(calls, 2);
  } finally { await view.close(); }
});
test('invalid-code message allows retry and successful reset returns to Login', async () => {
  let calls = 0; api.defaults.adapter = config => { calls++; return calls === 2 ? Promise.reject({ response: { status: 400, data: { code: 'INVALID_RESET_REQUEST' } } }) : Promise.resolve(response(config)); };
  const view = await mount();
  try {
    await requestCode(view); await fillReset(view); await act(async () => view.submit());
    assert.match(view.host.querySelector('[role="alert"]').textContent, /could not be verified/);
    assert.equal(view.host.querySelector('[type="submit"]').disabled, false);
    await act(async () => view.submit()); assert.match(view.host.textContent, /Login page/);
  } finally { await view.close(); }
});

test('institutional email popup isolates the recovery form and closes only through explicit action or safe Escape',async()=>{
 let requests=0;api.defaults.adapter=config=>{requests++;return Promise.resolve(response(config));};
 const view=await mount();try{
  await fill(view.host.querySelector('input'),'person@example.test');
  const trigger=view.host.querySelector('[type="submit"]');trigger.focus();await act(async()=>view.submit());
  const dialog=document.querySelector('[role=dialog]');assert.ok(dialog);assert.equal(dialog.getAttribute('aria-modal'),'true');assert.match(document.getElementById(dialog.getAttribute('aria-labelledby')).textContent,/Institutional email/);
  await act(async()=>dialog.parentElement.click());assert.ok(dialog.isConnected);assert.equal(requests,0);assert.ok(view.host.hasAttribute('inert'));assert.equal(document.activeElement.textContent,'Okay');
  await act(async()=>dialog.querySelector('button').click());assert.equal(document.querySelector('[role=dialog]'),null);assert.equal(document.activeElement,trigger);
  assert.equal(view.host.querySelector('input').value,'person@example.test');
  await act(async()=>view.submit());await act(async()=>document.querySelector('[role=dialog]').dispatchEvent(new window.KeyboardEvent('keydown',{key:'Escape',bubbles:true})));
  assert.equal(document.querySelector('[role=dialog]'),null);assert.equal(requests,0);
 }finally{await view.close();}
});
