import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';

let dom, server, createRoot, Router, AuthContext, Topbar, Shell, ProfessorShell, StudentShell, ProfessorContext, StudentContext;
let width = 1440;
const h = React.createElement;
before(async () => {
  dom = new JSDOM('<html><body></body></html>', { url: 'http://localhost' });
  for (const key of ['window', 'document', 'HTMLElement', 'Event', 'KeyboardEvent', 'localStorage', 'sessionStorage']) globalThis[key] = dom.window[key];
  globalThis.IS_REACT_ACT_ENVIRONMENT = true;
  window.matchMedia = () => ({ matches: width <= 1024, addEventListener() {}, removeEventListener() {} });
  ({ createRoot } = await import('react-dom/client'));
  Router = await import('react-router-dom');
  server = await createServer({ server: { middlewareMode: true }, appType: 'custom' });
  const load = path => server.ssrLoadModule('/src/' + path);
  ({ AuthContext } = await load('context/login/useAuth.js'));
  Topbar = (await load('components/shared/Topbar.jsx')).default;
  Shell = (await load('components/shared/PageShell.jsx')).default;
  ProfessorShell = (await load('pages/professor/components/ProfessorPageShell.jsx')).default;
  StudentShell = (await load('pages/student/components/StudentPageShell.jsx')).default;
  ({ ProfessorWorkflowContext: ProfessorContext } = await load('context/professor/useProfessorWorkflow.js'));
  ({ StudentWorkflowContext: StudentContext } = await load('context/student/useStudentWorkflow.js'));
});
after(async () => { await server?.close(); dom?.window.close(); });

async function mount(children) {
  const host = document.createElement('div'); document.body.append(host);
  const root = createRoot(host);
  await act(async () => root.render(h(Router.MemoryRouter, null,
    h(AuthContext.Provider, { value: { user: { name: 'Test identity' }, logout() {} } }, children))));
  return { host, async close() { await act(async () => root.unmount()); host.remove(); } };
}

test('all four roles share one header and retain enabled/disabled page actions', async () => {
  const headerClasses = new Set();
  for (const roleBadge of ['Superadmin', 'Admin', 'Professor', 'Student']) {
    let calls = 0;
    const ui = await mount(h(Shell, { responsive: true, roleBadge, navItems: [] },
      h(Topbar, { title: 'Account management' }, h('button', { onClick: () => calls++ }, 'Add account'), h('button', { disabled: true, onClick: () => calls++ }, 'Pending action')),
      h('main', null, 'Existing page content')));
    try {
      const header = ui.host.querySelector('[data-page-topbar]');
      headerClasses.add(header.className);
      assert.equal(header.querySelector('h1').textContent, 'Account management');
      assert.ok(ui.host.querySelector('aside'));
      assert.equal(ui.host.querySelector('main').textContent, 'Existing page content');
      await act(async () => { header.querySelectorAll('button')[0].click(); header.querySelectorAll('button')[1].click(); });
      assert.equal(calls, 1);
    } finally { await ui.close(); }
  }
  assert.equal(headerClasses.size, 1);
});

test('tablet and mobile keep the header, navigation drawer and safe dismissal functional', async () => {
  for (width of [768, 390]) {
    const ui = await mount(h(Shell, { responsive: true, roleBadge: 'Admin', navItems: [] }, h(Topbar, { title: 'A long page title with a useful action' }, h('button', null, 'Manage accounts'))));
    try {
      const toggle = ui.host.querySelector('[aria-label="Open navigation"]');
      assert.ok(toggle); assert.equal(ui.host.querySelector('aside'), null);
      await act(async () => toggle.click());
      const dialog = document.querySelector('[role="dialog"]');
      assert.ok(dialog); assert.ok(dialog.closest('body'));
      await act(async () => dialog.parentElement.click());
      assert.ok(dialog.isConnected);
      await act(async () => dialog.querySelector('[aria-label="Close navigation"]').click());
      assert.equal(document.querySelector('[role="dialog"]'), null);
      assert.equal(document.activeElement, toggle);
      assert.ok(ui.host.querySelector('[data-page-topbar] button'));
    } finally { await ui.close(); }
  }
  width = 1440;
});

test('Professor Preview and Acting banners remain before the header with working exit controls', async () => {
  for (const preview of [true, false]) {
    const exits = [];
    const ui = await mount(h(ProfessorContext.Provider, { value: { acting: true, preview, target: { name: 'Test Professor' }, basePath: '/admin/professor-mode', leave: value => exits.push(value) } },
      h(ProfessorShell, { responsive: true, navItems: [] }, h(Topbar, { title: 'Lesson Management' }), h('main', null, 'Lesson'))));
    try {
      const banner = ui.host.querySelector('section[aria-label]');
      assert.match(banner.textContent, preview ? /Read-only/ : /Changes affect real academic data and are audited/);
      assert.ok(banner.compareDocumentPosition(ui.host.querySelector('[data-page-topbar]')) & window.Node.DOCUMENT_POSITION_FOLLOWING);
      await act(async () => { for (const button of banner.querySelectorAll('button')) button.click(); });
      assert.deepEqual(exits, [true, false]);
    } finally { await ui.close(); }
  }
});

test('Student Preview and Support warnings and exit controls remain visible above the shared header', async () => {
  for (const preview of [true, false]) {
    const exits = [];
    const ui = await mount(h(StudentContext.Provider, { value: { acting: true, preview, target: { name: 'Test Student' }, basePath: '/admin/student-mode', leave: value => exits.push(value) } },
      h(StudentShell, null, h(Topbar, { title: 'Subjects' }), h('main', null, 'Subjects'))));
    try {
      const banner = ui.host.querySelector('[aria-label="Student acting mode"]');
      assert.match(banner.textContent, preview ? /No changes will be saved/ : /SUPPORT MODE - REAL STUDENT DATA/);
      assert.ok(banner.compareDocumentPosition(ui.host.querySelector('[data-page-topbar]')) & window.Node.DOCUMENT_POSITION_FOLLOWING);
      await act(async () => { for (const button of banner.querySelectorAll('button')) button.click(); });
      assert.deepEqual(exits, [true, false]);
    } finally { await ui.close(); }
  }
});

test('shared layout defines compact responsive spacing, wrapping, theme contrast and keyboard focus', async () => {
  const read = path => readFile(new URL('../src/' + path, import.meta.url), 'utf8');
  const navigation = await read('components/shared/Navigation.module.css');
  assert.match(navigation, /min-height: 76px/);
  assert.match(navigation, /min-height: 64px/);
  assert.match(navigation, /border-radius: var\(--radius-lg\)/);
  assert.match(navigation, /flex-wrap: wrap/);
  assert.match(navigation, /\.topbar :focus-visible/);
  assert.match(navigation, /var\(--nav-topbar\)/);
  assert.match(await read('components/shared/NavigationBrand.module.css'), /data-theme="dark"/);
  const shell = await read('components/shared/PageShell.module.css');
  assert.match(shell, /--page-gutter: var\(--space-6\)/);
  assert.match(shell, /--page-gutter: var\(--space-4\)/);
  const layout = await read('components/shared/PageLayout.module.css');
  assert.match(layout, /padding: 0 var\(--page-gutter, 24px\) var\(--page-gutter, 24px\)/);
  assert.match(layout, /row-gap: var\(--page-section-gap, 24px\)/);
});

test('every role page, lesson workspace, quiz, badges and mode selector uses the shared Topbar', async () => {
  const pages = {
    superadmin: ['Admins', 'SuperAdminDashboard', 'SuperAdminProfile'],
    admin: ['AdminDashboard', 'AdminProfile', 'Monitoring', 'ProfessorManagement', 'StudentManagement', 'SubjectManagement'],
    professor: ['ProfessorDashboard', 'ProfessorProfile', 'SubjectManagement', 'StudentProgress', 'LessonEditor'],
    student: ['StudentDashboard', 'Subjects', 'LessonChat', 'Quiz', 'Progress', 'Badges', 'Profile'],
  };
  for (const [role, names] of Object.entries(pages)) {
    for (const name of names) {
      const source = await readFile(new URL(`../src/pages/${role}/components/${name}.jsx`, import.meta.url), 'utf8');
      assert.match(source, /<Topbar[ >]/, `${role}/${name}`);
      assert.doesNotMatch(source, /<header className=\{styles\.(topbar|topHeader)\}/);
    }
  }
  for (const name of ['ProfessorMode', 'StudentMode']) {
    const source = await readFile(new URL(`../src/pages/admin/acting/${name}.jsx`, import.meta.url), 'utf8');
    assert.match(source, /<Topbar title=/);
  }
});
