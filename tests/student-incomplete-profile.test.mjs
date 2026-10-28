import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';

let dom, server, createRoot, Router, AuthContext, ToastContext, Workflow, Profile, Subjects;
before(async () => {
  dom = new JSDOM('<html><body></body></html>', { url: 'http://localhost' });
  for (const key of ['window', 'document', 'HTMLElement', 'Event', 'localStorage', 'sessionStorage']) globalThis[key] = dom.window[key];
  globalThis.IS_REACT_ACT_ENVIRONMENT = true;
  window.matchMedia = () => ({ matches: false, addEventListener() {}, removeEventListener() {} });
  ({ createRoot } = await import('react-dom/client'));
  Router = await import('react-router-dom');
  server = await createServer({ server: { middlewareMode: true }, appType: 'custom' });
  ({ AuthContext } = await server.ssrLoadModule('/src/context/login/useAuth.js'));
  ({ ToastContext } = await server.ssrLoadModule('/src/context/notifications/useToast.js'));
  ({ StudentWorkflowContext: Workflow } = await server.ssrLoadModule('/src/context/student/useStudentWorkflow.js'));
  Profile = (await server.ssrLoadModule('/src/pages/student/components/Profile.jsx')).default;
  Subjects = (await server.ssrLoadModule('/src/pages/student/components/Subjects.jsx')).default;
});
after(async () => { await server.close(); dom.window.close(); });

const incomplete = { userId: 26, name: 'Own Student', email: 'own@example.test', profileIncomplete: true, studentId: null, section: null, yearLevel: null };
const complete = { ...incomplete, profileIncomplete: false, studentId: '29AHRT-0168', section: 'BSHM 1A', yearLevel: 'FIRST_YEAR' };
async function mount(Page, api, acting = false) {
  const host = document.createElement('div'); document.body.append(host); const root = createRoot(host);
  const workflow = { api: { getProfilePhoto: async () => null, ...api }, acting, preview: acting, target: { name: 'Own Student' }, basePath: '/student', identity: 'student' };
  await act(async () => root.render(React.createElement(AuthContext.Provider, { value: { user: { name: 'Own Student', role: 'student' }, logout() {} } },
    React.createElement(ToastContext.Provider, { value: { showToast() {} } },
      React.createElement(Workflow.Provider, { value: workflow }, React.createElement(Router.MemoryRouter, {initialEntries:[Page===Subjects?'/student/subjects?subject=1':'/student/profile']}, React.createElement(Page)))))));
  return { host, cleanup: async () => { await act(async () => root.unmount()); host.remove(); } };
}
const button = (host, label) => [...host.querySelectorAll('button')].find(b => b.textContent === label);
test('incomplete profile retains own identity, safe placeholders and account controls; refresh shows verified data', async () => {
  let calls = 0;
  const view = await mount(Profile, { getProfileData: async () => ++calls === 1 ? incomplete : complete });
  try {
    assert.match(view.host.textContent, /Academic profile incomplete/);
    assert.match(view.host.textContent, /Contact an administrator/);
    assert.match(view.host.textContent, /own@example.test/);
    assert.equal([...view.host.querySelectorAll('span')].filter(s => s.textContent === 'Not set').length, 3);
    assert.doesNotMatch(view.host.textContent, /undefined|null|Couldn't load your profile/);
    assert.ok(view.host.querySelector('[aria-label="Change photo"]'));
    assert.ok(view.host.querySelector('[aria-label="Edit full name"]'));
    assert.equal(view.host.querySelector('input[name="studentId"]'), null);
    await act(async () => button(view.host, 'Check again').click());
    assert.equal(calls, 2); assert.doesNotMatch(view.host.textContent, /Academic profile incomplete/);
    assert.match(view.host.textContent, /29AHRT-0168/); assert.match(view.host.textContent, /BSHM 1A/);
  } finally { await view.cleanup(); }
});
test('subjects explain incomplete profile, retry read-only request and retain loading state until recovery completes', async () => {
  let calls = 0, finish;
  const view = await mount(Subjects, { getSubjectsData: () => {
    calls++;
    if (calls === 1) return Promise.reject({ response: { status: 409, data: { code: 'PROFILE_INCOMPLETE' } } });
    return new Promise(resolve => { finish = resolve; });
  } });
  try {
    assert.match(view.host.textContent, /Academic profile incomplete/);
    assert.equal(view.host.querySelector('[aria-label="Search subjects"]'), null);
    await act(async () => button(view.host, 'Check again').click());
    assert.match(view.host.textContent, /Loading subjects/); assert.equal(button(view.host, 'Check again'), undefined);
    assert.equal(calls, 2);
    await act(async () => finish([{ subjectId: 1, enrolled:true, subjectName: 'Hospitality', yearLevel: 'FIRST_YEAR', totalWeeks: 0, completedWeeks: 0 }]));
    assert.doesNotMatch(view.host.textContent, /Academic profile incomplete/); assert.match(view.host.textContent, /Hospitality/);
  } finally { await view.cleanup(); }
});
test('unrelated subject failures are not mislabeled as incomplete profiles', async () => {
  const view = await mount(Subjects, { getSubjectsData: async () => { throw { response: { status: 500, data: { code: 'INTERNAL_SERVER_ERROR' } } }; } });
  try { assert.match(view.host.textContent, /Couldn't load subjects/); assert.doesNotMatch(view.host.textContent, /Academic profile incomplete/); }
  finally { await view.cleanup(); }
});
test('normal complete profiles and authorized Preview rendering remain read-only without recovery writes', async () => {
  for (const acting of [false, true]) {
    const view = await mount(Profile, { getProfileData: async () => complete }, acting);
    try {
      assert.doesNotMatch(view.host.textContent, /Academic profile incomplete/);
      assert.match(view.host.textContent, /29AHRT-0168/);
      assert.equal(Boolean(view.host.querySelector('[aria-label="Edit full name"]')), !acting);
      assert.equal(Boolean(view.host.querySelector('[aria-label="Change photo"]')), !acting);
    } finally { await view.cleanup(); }
  }
});
