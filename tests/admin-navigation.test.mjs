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
  const { ADMIN_NAV_ITEMS } = await server.ssrLoadModule('/src/data/navigationData.js');
  Shell = () => React.createElement(PageShell, { responsive: true, navItems: ADMIN_NAV_ITEMS, roleBadge: 'Admin' });
  pages = await Promise.all(['AdminDashboard', 'ProfessorManagement', 'StudentManagement', 'Monitoring', 'AdminProfile', 'SubjectManagement'].map(async name => (await server.ssrLoadModule(`/src/pages/admin/components/${name}.jsx`)).default));
});
after(async () => { await server.close(); dom.window.close(); });

async function mount(Component, route = '/admin', logout = () => {}) {
  const host = document.createElement('div'); document.body.append(host);
  const root = createRoot(host);
  function Location() { return React.createElement('output', {}, Router.useLocation().pathname); }
  await act(async () => root.render(React.createElement(AuthContext.Provider, { value: { user: { name: 'Admin' }, logout } },
    React.createElement(ToastContext.Provider, { value: { showToast() {} } },
      React.createElement(Router.MemoryRouter, { initialEntries: [route] },
        React.createElement(Router.Routes, {}, React.createElement(Router.Route, { path: '*', element: React.createElement(Component) })), React.createElement(Location))))));
  return async () => { await act(async () => root.unmount()); host.remove(); };
}
const click = async element => act(async () => element.click());

test('all six Admin pages retain navigation during loading at desktop, tablet and mobile breakpoints', async () => {
  for (width of [1440, 768, 390]) {
    for (const Page of pages) {
      const cleanup = await mount(Page);
      try {
        const toggle = document.querySelector('[aria-label="Open navigation"]');
        if (width > 1024) { assert.equal(toggle, null); assert.equal(document.querySelectorAll('nav a').length, 5); }
        else {
          assert.doesNotMatch(document.body.textContent, /Superadmin|Super Admin/); assert.ok(toggle); assert.equal(document.querySelector('aside'), null);
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
  const cleanup = await mount(Shell, '/admin/professors', () => { logouts++; });
  try {
    let toggle = document.querySelector('[aria-label="Open navigation"]');
    await click(toggle);
    assert.equal(document.querySelector('nav a[aria-current="page"]').getAttribute('href'), '/admin/professors');
    assert.deepEqual([...document.querySelectorAll('nav a')].map(a => a.getAttribute('href')), ['/admin', '/admin/professors', '/admin/students', '/admin/monitoring', '/admin/profile']);
    await act(async () => document.querySelector('[role="dialog"]').dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })));
    assert.equal(document.querySelector('[role="dialog"]'), null);
    assert.equal(document.activeElement, toggle);
    await click(toggle);
    await click(document.querySelector('a[href="/admin/monitoring"]'));
    assert.equal(document.querySelector('output').textContent, '/admin/monitoring');
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


test('Admin account tables keep their columns in a focusable local scroll region', async () => {
 width=390;
 api.defaults.adapter=async config=>({status:200,headers:{},config,data:[{id:1,name:'Long account name',email:'long@example.test',professorId:'P1',status:'ACTIVE',yearLevel:'FIRST_YEAR'}]});
 for(const index of [1,2]){
  const cleanup=await mount(pages[index]);
  try {
   const region=document.querySelector('[role="region"]');
   assert.ok(region);assert.equal(region.tabIndex,0);
   assert.match(region.getAttribute('aria-label'),/scroll horizontally/);
   assert.match(region.textContent,/Long account name/);assert.match(region.textContent,/Actions/);
  }finally{await cleanup();}
 }
});

test('AD-15 Professor header and rows share exactly five columns and retain local scrolling', async () => {
 const {readFile}=await import('node:fs/promises');
 const css=await readFile(new URL('../src/pages/admin/styles/ProfessorManagement.module.css',import.meta.url),'utf8');
 const columns=selector=>css.match(new RegExp('\\.'+selector+'\\s*\\{[^}]*grid-template-columns:\\s*([^;]+);'))[1].trim().split(/\s+/);
 assert.deepEqual(columns('tableHeaderRow'),columns('tableRow'));
 assert.equal(columns('tableRow').length,5);
 assert.match(css,/\.tableCard\s*\{[^}]*overflow-x:\s*auto/);
 assert.match(css,/\.tableHeaderRow,\s*\.tableRow\s*\{\s*min-width:\s*760px/);
 for(width of [1440,390]){
  api.defaults.adapter=async config=>({status:200,headers:{},config,data:[{id:1,name:'Professor',email:'prof@example.test',professorId:'P1',status:'ACTIVE'}]});
  const cleanup=await mount(pages[1],'/admin/professors');
  try{
   const region=document.querySelector('[role="region"]');
   const [header,row]=region.querySelector('[role="table"]').children;
   assert.deepEqual([...header.children].map(c=>c.textContent),['Name','Email','Professor ID','Status','Actions']);
   assert.equal(row.children.length,5);
   for(const cell of row.children) {
    assert.equal(cell.getAttribute('role'),'cell');
    assert.equal(document.getElementById(cell.getAttribute('aria-describedby')).getAttribute('role'),'columnheader');
   }
   assert.equal(document.querySelector('input').getAttribute('aria-label'),'Search professors');
   assert.ok(row.querySelector('[aria-label="Edit"]'));
   assert.ok(row.querySelector('[aria-label="Deactivate"]'));
  }finally{await cleanup();}
 }
});
