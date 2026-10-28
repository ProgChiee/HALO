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

async function mount(Component, route = '/student', logout = () => {}, name = 'Student') {
  const host = document.createElement('div'); document.body.append(host);
  const root = createRoot(host);
  function Location() { return React.createElement('output', {}, Router.useLocation().pathname); }
  await act(async () => root.render(React.createElement(AuthContext.Provider, { value: { user: { name }, logout } },
    React.createElement(ToastContext.Provider, { value: { showToast() {} } },
      React.createElement(Router.MemoryRouter, { initialEntries: [route] },
        React.createElement(Router.Routes, {}, React.createElement(Router.Route, { path: '/login', element: null }), React.createElement(Router.Route, { path: '*', element: React.createElement(Component) })), React.createElement(Location))))));
  return async () => { await act(async () => root.unmount()); host.remove(); };
}
const click = async element => act(async () => element.click());

test('all seven Student pages retain navigation during loading at desktop, tablet and mobile breakpoints', async () => {
  for (width of [1440, 768, 390]) {
    for (const Page of pages) {
      const cleanup = await mount(Page, Page===pages[1]?'/student/subjects?subject=1':'/student');
      try {
        const toggle = document.querySelector('[aria-label="Open navigation"]');
        if (width > 1024) { assert.equal(toggle, null); assert.equal(document.querySelectorAll('aside nav a').length, 7); }
        else {
          assert.ok(toggle); assert.equal(document.querySelector('aside'), null);
          await click(toggle);
          assert.equal(toggle.getAttribute('aria-expanded'), 'true');
          assert.equal(document.querySelectorAll('[role="dialog"] nav a').length, 7);
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
  const cleanup = await mount(Shell, '/student/subjects/my', () => { logouts++; });
  try {
    let toggle = document.querySelector('[aria-label="Open navigation"]');
    await click(toggle);
    assert.equal(document.querySelector('nav a[aria-current="page"]').getAttribute('href'), '/student/subjects/my');
    assert.deepEqual([...document.querySelectorAll('nav a')].map(a => a.getAttribute('href')), ['/student', '/student/subjects/my', '/student/subjects/browse', '/student/progress', '/student/badges', '/student/profile', '/student/settings']);
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
    assert.equal(logouts, 0);
    await act(async () => [...document.querySelectorAll('[role="dialog"] button')].find(button => button.textContent === 'Log Out').click());
    assert.equal(logouts, 1);
    assert.equal(document.querySelector('output').textContent, '/login');
    assert.equal(document.querySelector('[role="dialog"]'), null);
    assert.equal(document.querySelector('[inert]'), null);
  } finally { await cleanup(); }
});


test('Student submenu follows direct routes in normal, Preview and Support contexts and retains focus on toggle', async()=>{
 width=1440;
 const {StudentWorkflowContext}=await server.ssrLoadModule('/src/context/student/useStudentWorkflow.js');
 for(const base of ['/student','/admin/student-preview/test','/admin/student-mode/test']){
  for(const child of ['my','browse']){
   const workflow={basePath:base,acting:base!='/student',preview:base.includes('preview'),target:{name:'Target Student'},leave(){}};
   const Component=()=>React.createElement(StudentWorkflowContext.Provider,{value:workflow},React.createElement(Shell));
   const cleanup=await mount(Component,base+'/subjects/'+child);
   try{
    const aside=document.querySelector('aside');assert.match(aside.className,/studentSidebar/);
    const button=aside.querySelector('[data-navigation-group]'),submenu=document.getElementById(button.getAttribute('aria-controls'));
    assert.equal(button.getAttribute('aria-expanded'),'true');assert.equal(submenu.hidden,false);
    assert.equal(submenu.querySelector('[aria-current="page"]').getAttribute('href'),base+'/subjects/'+child);
    button.focus();await click(button);assert.equal(submenu.hidden,true);assert.equal(document.activeElement,button);
    await click(button);assert.equal(submenu.hidden,false);assert.equal(document.activeElement,button);
    assert.ok([...aside.querySelectorAll('a')].every(a=>a.getAttribute('href').startsWith(base)));
   }finally{await cleanup();}
  }
 }
});

test('Student polish is scoped and keeps compact targets, scrollable navigation and safe footer padding',async()=>{
 const {readFile}=await import('node:fs/promises');
 const css=await readFile(new URL('../src/components/shared/Sidebar.module.css',import.meta.url),'utf8');
 const source=await readFile(new URL('../src/components/shared/Sidebar.jsx',import.meta.url),'utf8');
 assert.match(source,/roleBadge === 'Student' \? styles.studentSidebar/);
 assert.match(css,/\.studentSidebar \.nav \{ gap: 2px/);
 assert.match(css,/\.studentSidebar \.navItem \{[^}]+min-height: 44px/);
 assert.match(css,/\.studentSidebar \.submenu \{[^}]+border-left: 1px solid var\(--nav-border\)/);
 assert.match(css,/env\(safe-area-inset-bottom, 0px\)/);
 assert.match(css,/\.studentSidebar \.userName \{[^}]+text-overflow: ellipsis/);
 assert.match(css,/\.studentSidebar \.logoutBtn \{ width: 44px; height: 44px/);
 assert.match(css,/\.studentSidebar \.section \{[^}]+min-height: 0; overflow-y: auto/);
 assert.match(css,/@media \(max-height: 399px\)/);
 assert.match(css,/\.studentSidebar \.logoutBtn:focus-visible/);
 const Shared=(await server.ssrLoadModule('/src/components/shared/PageShell.jsx')).default;
 for(const roleBadge of ['Admin','Professor','Superadmin']){
  const cleanup=await mount(()=>React.createElement(Shared,{roleBadge}));
  try{assert.doesNotMatch(document.querySelector('aside').className,/studentSidebar/);}finally{await cleanup();}
 }
});


test('long authenticated names remain intact and logout still requires explicit confirmation',async()=>{
 width=1440;let calls=0;const name='A very long authenticated Student name that must not overlap the logout control';
 const cleanup=await mount(Shell,'/student',()=>{calls++;},name);
 try{
  const aside=document.querySelector('aside');assert.ok(aside.textContent.includes(name));
  const logout=aside.querySelector('[aria-label="Log out"]');assert.ok(logout);
  await click(logout);assert.equal(calls,0);
  await click([...document.querySelectorAll('[role="dialog"] button')].find(b=>b.textContent==='Cancel'));
  assert.equal(calls,0);assert.equal(document.activeElement,logout);
 }finally{await cleanup();}
});
