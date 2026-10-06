import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';
import { logProfessorError } from '../src/utils/professorDiagnostics.js';
import { professorErrorMessage } from '../src/utils/professorErrors.js';
import { apiErrorMessage } from '../src/utils/apiErrors.js';

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
      assert.match(host.textContent, /do not have permission/i);
      assert.ok(calls.some(call => call[0] === '[HALO Professor]' && call[1].status === 403));
      assert.ok(!JSON.stringify(calls).includes(secret));
      api.defaults.adapter = async config => ({ status: 200, headers: {}, config, data: { name: 'Recovered Professor' } });
      await act(async () => [...host.querySelectorAll('button')].find(button => button.textContent === 'Retry').click());
      assert.doesNotMatch(host.textContent, /do not have permission/i);
      assert.ok(!host.textContent.includes(secret));
    } finally {
      await act(async () => root.unmount()); host.remove(); console.error = originalError; console.warn = originalWarn; api.defaults.adapter = adapter;
    }
  }
});

test('Professor error messages are actionable and never display arbitrary server details', () => {
  for (const [status, code, expected] of [
    [400, 'VALIDATION_FAILED', /required fields/], [401, 'AUTHENTICATION_REQUIRED', /sign in/],
    [403, 'ACCESS_DENIED', /permission/], [404, 'RESOURCE_NOT_FOUND', /unavailable/],
    [409, 'SUBJECT_CODE_ALREADY_EXISTS', /another code/], [409, 'STALE_MODULE_OPERATION', /Reload/],
    [415, 'UPLOAD_FILE_UNSUPPORTED', /PDF/], [400, 'INVALID_MULTIPART_REQUEST', /Select your files/],
    [500, 'UNKNOWN', /try again later/],
  ]) {
    const message = professorErrorMessage({ response: { status, data: { code, message: secret } } });
    assert.match(message, expected); assert.ok(!message.includes(secret));
  }
  assert.match(apiErrorMessage({ response: { data: { code: 'INCORRECT_CURRENT_PASSWORD', message: secret } } }), /current password is incorrect/);
  assert.match(professorErrorMessage({ code: 'ERR_NETWORK' }), /connection/);
  assert.match(professorErrorMessage({ code: 'ETIMEDOUT' }), /current state/);
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

test('Professor Profile password dialog and Lesson Editor controls expose accessible names', async () => {
 for(const name of ['ProfessorProfile','LessonEditor']){
  const Component=(await server.ssrLoadModule('/src/pages/professor/components/'+name+'.jsx')).default;
  const host=document.createElement('div');document.body.append(host);const root=createRoot(host);const adapter=api.defaults.adapter;
  api.defaults.adapter=async config=>({status:name==='LessonEditor'?204:200,headers:{},config,data:name==='LessonEditor'?null:{name:'Professor',email:'prof@example.test'}});
  try{
   await act(async()=>root.render(React.createElement(AuthContext.Provider,{value:{user:{name:'Professor'},logout(){}}},React.createElement(ToastContext.Provider,{value:{showToast(){}}},React.createElement(Router.MemoryRouter,{},React.createElement(Component))))));
   if(name==='ProfessorProfile'){
    const trigger=[...host.querySelectorAll('button')].find(b=>/change password/i.test(b.textContent));trigger.focus();await act(async()=>trigger.click());
    const dialog=document.querySelector('[role="dialog"]');assert.ok(dialog);assert.equal(document.getElementById(dialog.getAttribute('aria-labelledby')).textContent,'Change Password');assert.equal(document.activeElement.id,'current-password');
    const save=[...dialog.querySelectorAll('button')].find(b=>b.textContent==='Save Changes');
    for(let i=0;i<4;i++)await act(async()=>dialog.dispatchEvent(new dom.window.KeyboardEvent('keydown',{key:'Tab',bubbles:true})));
    assert.equal(document.activeElement,save);
    await act(async()=>dialog.dispatchEvent(new dom.window.KeyboardEvent('keydown',{key:'Escape',bubbles:true})));assert.equal(document.querySelector('[role="dialog"]'),null);assert.equal(document.activeElement,trigger);
   }else{
    const fields=[...host.querySelectorAll('input,select,textarea')];assert.equal(fields.length,4);
    for(const field of fields)assert.ok(field.labels?.length || field.getAttribute('aria-label'));
   }
  }finally{await act(async()=>root.unmount());host.remove();api.defaults.adapter=adapter;}
 }
});
