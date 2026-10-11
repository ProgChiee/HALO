import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';

let dom, server, createRoot, Router, AuthContext, ToastContext, Shell, pages, api;
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
  api = (await server.ssrLoadModule('/src/services/apiClient.js')).default;
  api.defaults.adapter = () => new Promise(() => {});
  const PageShell = (await server.ssrLoadModule('/src/components/shared/PageShell.jsx')).default;
  const { PROFESSOR_NAV_ITEMS } = await server.ssrLoadModule('/src/data/navigationData.js');
  Shell = () => React.createElement(PageShell, { responsive: true, navItems: PROFESSOR_NAV_ITEMS, roleBadge: 'Professor' });
  pages = await Promise.all(['ProfessorDashboard', 'SubjectManagement', 'LessonEditor', 'StudentProgress', 'ProfessorProfile'].map(async name => (await server.ssrLoadModule(`/src/pages/professor/components/${name}.jsx`)).default));
});
after(async () => { await server.close(); dom.window.close(); });

async function mount(Component, route = '/professor', logout = () => {}) {
  const host = document.createElement('div'); document.body.append(host);
  const root = createRoot(host);
  function Location() { return React.createElement('output', {}, Router.useLocation().pathname); }
  await act(async () => root.render(React.createElement(AuthContext.Provider, { value: { user: { name: 'Professor' }, logout } },
    React.createElement(ToastContext.Provider, { value: { showToast() {} } },
      React.createElement(Router.MemoryRouter, { initialEntries: [route] },
        React.createElement(Router.Routes, {}, React.createElement(Router.Route, { path: '*', element: React.createElement(Component) })), React.createElement(Location))))));
  return async () => { await act(async () => root.unmount()); host.remove(); };
}
const click = async element => act(async () => element.click());

test('all five Professor pages retain navigation during loading at desktop, tablet and mobile breakpoints', async () => {
  for (width of [1440, 768, 390]) {
    for (const Page of pages) {
      const cleanup = await mount(Page);
      try {
        const toggle = document.querySelector('[aria-label="Open navigation"]');
        if (width > 1024) { assert.equal(toggle, null); assert.equal(document.querySelectorAll('nav a').length, 4); }
        else {
          assert.ok(toggle); assert.equal(document.querySelector('aside'), null);
          await click(toggle);
          assert.equal(toggle.getAttribute('aria-expanded'), 'true');
          assert.equal(document.querySelectorAll('[role="dialog"] nav a').length, 4);
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
  const cleanup = await mount(Shell, '/professor/subjects', () => { logouts++; });
  try {
    let toggle = document.querySelector('[aria-label="Open navigation"]');
    await click(toggle);
    assert.equal(document.querySelector('nav a[aria-current="page"]').getAttribute('href'), '/professor/subjects');
    assert.deepEqual([...document.querySelectorAll('nav a')].map(a => a.getAttribute('href')), ['/professor', '/professor/subjects', '/professor/progress', '/professor/profile']);
    await act(async () => document.querySelector('[role="dialog"]').dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })));
    assert.equal(document.querySelector('[role="dialog"]'), null);
    assert.equal(document.activeElement, toggle);
    await click(toggle);
    await click(document.querySelector('a[href="/professor/progress"]'));
    assert.equal(document.querySelector('output').textContent, '/professor/progress');
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

test('progress keeps all five columns in a keyboard-focusable scroll region', async () => {
 width=390;
 api.defaults.adapter=async config=>({status:200,headers:{},config,data:{content:[{userId:1,name:'Long student name',section:'Section A',completedModules:2,passedAssessments:3,totalBadges:4}],totalElements:1,number:0,totalPages:1,first:true,last:true}});
 const cleanup=await mount(pages[3],'/professor/progress');
 try{
  const region=document.querySelector('[aria-label="Student progress table, scroll horizontally to view all columns"]');assert.ok(region);assert.equal(region.tabIndex,0);assert.match(region.getAttribute('aria-label'),/scroll horizontally/);
  assert.equal(region.querySelectorAll('thead th[scope="col"]').length,5);assert.equal(region.querySelectorAll('tbody tr:first-child td').length,5);assert.match(region.textContent,/Long student name/);
 }finally{await cleanup();}
});
