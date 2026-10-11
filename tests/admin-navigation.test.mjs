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
        if (width > 1024) { assert.equal(toggle, null); assert.equal(document.querySelectorAll('nav a').length, 7); }
        else {
          assert.doesNotMatch(document.body.textContent, /Superadmin|Super Admin/); assert.ok(toggle); assert.equal(document.querySelector('aside'), null);
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
  const cleanup = await mount(Shell, '/admin/professors', () => { logouts++; });
  try {
    let toggle = document.querySelector('[aria-label="Open navigation"]');
    await click(toggle);
    assert.equal(document.querySelector('nav a[aria-current="page"]').getAttribute('href'), '/admin/professors');
    assert.deepEqual([...document.querySelectorAll('nav a')].map(a => a.getAttribute('href')), ['/admin', '/admin/professors', '/admin/students', '/admin/monitoring', '/admin/professor-mode', '/admin/student-mode', '/admin/profile']);
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
   assert.deepEqual([...header.children].map(c=>c.textContent),['Name','Email','Professor ID','Account Action','Actions']);
   assert.equal(row.children.length,5);
   for(const cell of row.children) {
    assert.equal(cell.getAttribute('role'),'cell');
    assert.equal(document.getElementById(cell.getAttribute('aria-describedby')).getAttribute('role'),'columnheader');
   }
   assert.equal(document.querySelector('input').getAttribute('aria-label'),'Search professors');
   assert.ok(row.querySelector('[aria-label="Edit"]'));
   assert.equal(row.children[3].querySelector('button').textContent.trim(),'Deactivate');
   assert.deepEqual([...row.children[4].querySelectorAll('button')].map(b=>b.textContent.trim()),['View','Edit']);
  }finally{await cleanup();}
 }
});


test('branded Admin navigation keeps logo first and groups destinations in desktop and drawer', async () => {
  for (width of [1440, 768, 390]) {
    const cleanup = await mount(Shell, '/admin');
    try {
      if (width <= 1024) await click(document.querySelector('[aria-label="Open navigation"]'));
      const sidebar = document.querySelector('aside');
      assert.equal(sidebar.firstElementChild.querySelector('img').alt, 'HALO');
      assert.match(sidebar.firstElementChild.querySelector('img').getAttribute('src'), /Icon\.png/);
      assert.equal(sidebar.children[1].textContent.trim(), 'Admin');
      const nav = sidebar.querySelector('nav');
      assert.deepEqual([...nav.querySelectorAll('p')].map(p => p.textContent), ['Main', 'Modes', 'Account']);
      assert.deepEqual([...nav.querySelectorAll('a')].map(a => a.textContent.trim()), ['Dashboard', 'Professor Management', 'Student Management', 'Monitoring', 'Professor Mode', 'Student Mode', 'Profile']);
      assert.equal(nav.querySelector('[aria-current="page"]').textContent.trim(), 'Dashboard');
      assert.ok(sidebar.querySelector('[aria-label="Log out"]'));
    } finally { await cleanup(); }
  }
});


test('all role and acting menus share logo-first sections without changing destinations', async () => {
  const PageShell = (await server.ssrLoadModule('/src/components/shared/PageShell.jsx')).default;
  const menus = await server.ssrLoadModule('/src/data/navigationData.js');
  const cases = [
    ['Superadmin', menus.SUPERADMIN_NAV_ITEMS], ['Admin', menus.ADMIN_NAV_ITEMS],
    ['Professor', menus.PROFESSOR_NAV_ITEMS], ['Student', menus.STUDENT_NAV_ITEMS],
    ['Professor', menus.PROFESSOR_NAV_ITEMS.map(item => ({ ...item, path: item.path.replace('/professor', '/admin/professor-mode/test-session') }))],
    ['Student', menus.STUDENT_NAV_ITEMS.map(item => ({ ...item, path: item.path.replace('/student', '/admin/student-mode/test-session') }))],
  ];
  for (width of [1440, 768, 390]) {
    for (const [roleBadge, navItems] of cases) {
      const Page = () => React.createElement(PageShell, { responsive: true, roleBadge, navItems });
      const cleanup = await mount(Page, navItems[0].path);
      try {
        if (width <= 1024) await click(document.querySelector('[aria-label="Open navigation"]'));
        const aside = document.querySelector('aside');
        assert.equal(aside.firstElementChild.querySelector('img').alt, 'HALO');
        assert.equal(aside.children[1].textContent, roleBadge === 'Superadmin' ? 'Super Admin' : roleBadge);
        assert.deepEqual([...aside.querySelectorAll('nav a')].map(a => a.getAttribute('href')), navItems.map(item => item.path));
        assert.equal(aside.querySelector('nav a').textContent.trim(), 'Dashboard');
        assert.equal(aside.querySelector('nav a[aria-current="page"]').getAttribute('href'), navItems[0].path);
        const sections = [...aside.querySelectorAll('nav p')].map(p => p.textContent);
        assert.deepEqual(sections, roleBadge === 'Admin' ? ['Main', 'Modes', 'Account'] : ['Main', 'Account']);
      } finally { await cleanup(); }
    }
  }
});

test('account action reflects real status for Professor and Student without duplicate controls', async()=>{
 for(const index of [1,2])for(const status of ['ACTIVE','INACTIVE']){
  let record={id:1,name:'Account',email:'account@example.test',professorId:'P1',status};
  api.defaults.adapter=async config=>{if(config.method!=='get')record={...record,status:JSON.parse(config.data).status};return {status:200,headers:{},config,data:config.method==='get'?[record]:record};};
  const cleanup=await mount(pages[index]);try{
   const table=document.querySelector('[role="table"]');assert.match(table.textContent,/Account Action/);
   const row=table.querySelectorAll('[role="row"]')[1];const cell=row.children[index===1?3:4];
   assert.equal(cell.textContent.trim(),status==='ACTIVE'?'Deactivate':'Reactivate');
   assert.deepEqual([...row.lastElementChild.querySelectorAll('button')].map(b=>b.textContent.trim()),index===1?['View','Edit']:['View']);
   await click(cell.querySelector('button'));assert.equal(document.querySelector('[role="dialog"]'),null);
   assert.equal(cell.textContent.trim(),status==='ACTIVE'?'Reactivate':'Deactivate');
  }finally{await cleanup();}
 }
});
