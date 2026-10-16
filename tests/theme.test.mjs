import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';
const script=readFileSync(new URL('../public/theme.js',import.meta.url),'utf8');
function environment(saved, dark=false) {
 const dom=new JSDOM('<html><body></body></html>',{url:'http://localhost',runScripts:'outside-only'});
 let change;const media={matches:dark,addEventListener(name,fn){change=fn;}};
 dom.window.matchMedia=()=>media;
 if(saved!==undefined)dom.window.localStorage.setItem('halo-theme-admin:test',saved);
 dom.window.eval(script);
 return {dom,system(value){media.matches=value;change();}};
}
test('theme selections persist, restore, follow System and reject invalid saved values',()=>{
 const {dom,system}=environment('dark');const w=dom.window;
 try {
  assert.equal(w.document.documentElement.dataset.theme,'light');
  w.haloTheme.bindAccount('admin:test');
  assert.equal(w.document.documentElement.dataset.theme,'dark');
  for(const value of ['light','dark','system']){w.haloTheme.set(value);assert.equal(w.localStorage.getItem('halo-theme-admin:test'),value);assert.equal(w.haloTheme.get(),value);}
  system(true);assert.equal(w.document.documentElement.dataset.theme,'dark');system(false);assert.equal(w.document.documentElement.dataset.theme,'light');
  w.haloTheme.set('light');system(true);assert.equal(w.document.documentElement.dataset.theme,'light');
  w.haloTheme.set('dark');w.eval(script);assert.equal(w.haloTheme.get(),'light');w.haloTheme.bindAccount('admin:test');assert.equal(w.haloTheme.get(),'dark');
  w.localStorage.setItem('halo-theme-admin:test','invalid');w.dispatchEvent(new w.StorageEvent('storage',{key:'halo-theme-admin:test'}));assert.equal(w.haloTheme.get(),'light');
 }finally{dom.window.close();}
 const invalid=environment('invalid');assert.equal(invalid.dom.window.document.documentElement.dataset.theme,'light');invalid.dom.window.close();
 const fresh=environment();assert.equal(fresh.dom.window.haloTheme.get(),'light');fresh.dom.window.close();
});
let dom,server,createRoot,Router,AuthContext,ToastContext,Settings,Routes,nav,AccountTheme;
before(async()=>{
 ({dom}=environment());for(const key of ['window','document','HTMLElement','Event','localStorage','sessionStorage'])globalThis[key]=dom.window[key];
 window.matchMedia=()=>({matches:false,addEventListener(){},removeEventListener(){}});
 globalThis.IS_REACT_ACT_ENVIRONMENT=true;({createRoot}=await import('react-dom/client'));Router=await import('react-router-dom');
 server=await createServer({server:{middlewareMode:true},appType:'custom'});
 ({AuthContext}=await server.ssrLoadModule('/src/context/login/useAuth.js'));({ToastContext}=await server.ssrLoadModule('/src/context/notifications/useToast.js'));
 Settings=(await server.ssrLoadModule('/src/components/shared/Settings.jsx')).default;Routes=(await server.ssrLoadModule('/src/routes/AppRoutes.jsx')).default;
 AccountTheme=(await server.ssrLoadModule('/src/components/AccountTheme.jsx')).default;
 nav=await server.ssrLoadModule('/src/data/navigationData.js');
});
after(async()=>{await server.close();dom.window.close();});
function tree(component,role,path){return React.createElement(AuthContext.Provider,{value:{user:{name:'User'},role,isAuthenticated:true,loading:false,logout(){}}},React.createElement(ToastContext.Provider,{value:{showToast(){}}},React.createElement(Router.MemoryRouter,{initialEntries:[path]},component)));}
test('Settings page exposes keyboard radio choices and updates document theme',async()=>{
 const host=document.createElement('div');document.body.append(host);const root=createRoot(host);
 try {window.haloTheme.bindAccount('admin:test');await act(async()=>root.render(tree(React.createElement(Settings,{role:'student'}),'student','/student/settings')));
  for(const value of ['dark','system','light']){const radio=host.querySelector(`input[value="${value}"]`);await act(async()=>radio.click());assert.equal(radio.checked,true);assert.equal(window.localStorage.getItem('halo-theme-admin:test'),value);}
 }finally{await act(async()=>root.unmount());host.remove();}
});
test('all real roles have guarded Settings routes and Account navigation after Profile',async()=>{
 for(const [role,key] of [['admin','ADMIN'],['superadmin','SUPERADMIN'],['professor','PROFESSOR'],['student','STUDENT']]) {
  const items=nav[key+'_NAV_ITEMS'],index=items.findIndex(i=>i.label==='Profile');assert.equal(items[index+1].label,'Settings');assert.equal(items[index+1].section,'Account');
  const host=document.createElement('div');document.body.append(host);const root=createRoot(host);
  try {await act(async()=>{root.render(tree(React.createElement(Routes),role,`/${role}/settings`));});
   // Allow the real lazy route import to settle.
   for(let i=0;i<30&&!host.querySelector('fieldset');i++)await act(async()=>{await new Promise(r=>setTimeout(r,20));});
   assert.equal(host.querySelector('h1')?.textContent,'Settings');assert.ok(host.querySelector('a[aria-current="page"][href="/'+role+'/settings"]'));
  }finally{await act(async()=>root.unmount());host.remove();}
 }
});

test('unavailable browser storage does not prevent theme changes',()=>{
 const {dom}=environment();
 try {
  Object.defineProperty(dom.window,'localStorage',{get(){throw new Error('Storage unavailable');}});
  dom.window.eval(script);assert.equal(dom.window.haloTheme.get(),'light');
  dom.window.haloTheme.bindAccount('admin:test');dom.window.haloTheme.set('dark');assert.equal(dom.window.document.documentElement.dataset.theme,'dark');
 }finally{dom.window.close();}
});
test('Settings keeps the existing role guard',async()=>{
 const host=document.createElement('div');document.body.append(host);const root=createRoot(host);
 try {
  await act(async()=>root.render(tree(React.createElement(Routes),'student','/admin/settings')));
  for(let i=0;i<20&&!host.textContent.includes('403');i++)await act(async()=>{await new Promise(r=>setTimeout(r,20));});
  assert.equal(host.querySelector('fieldset'),null);assert.notEqual(host.querySelector('h1')?.textContent,'Settings');
 }finally{await act(async()=>root.unmount());host.remove();}
});

test('accounts remain isolated, logout is light, returning users restore and legacy preference is ignored',()=>{
 const {dom}=environment();const w=dom.window;
 try {
  w.localStorage.setItem('halo-theme','dark');w.eval(script);
  assert.equal(w.haloTheme.get(),'light');w.haloTheme.set('dark');assert.equal(w.haloTheme.get(),'light');
  w.haloTheme.bindAccount('superadmin:one');w.haloTheme.set('dark');
  w.haloTheme.bindAccount(null);assert.equal(w.document.documentElement.dataset.theme,'light');
  assert.equal(w.localStorage.getItem('halo-theme-superadmin:one'),'dark');
  w.haloTheme.bindAccount('student:two');assert.equal(w.haloTheme.get(),'light');
  w.haloTheme.set('system');w.haloTheme.bindAccount('superadmin:one');assert.equal(w.haloTheme.get(),'dark');
  w.haloTheme.bindAccount('student:two');assert.equal(w.haloTheme.get(),'system');
 }finally{w.close();}
});

test('Auth routes stay light and every acting route inherits the authenticated Admin preference',async()=>{
 const host=document.createElement('div');document.body.append(host);const root=createRoot(host);
 const render=async(path,authenticated=true)=>act(async()=>root.render(
  React.createElement(AuthContext.Provider,{value:{user:{email:'admin@test.com',role:'admin'},isAuthenticated:authenticated}},
   React.createElement(Router.MemoryRouter,{key:path+authenticated,initialEntries:[path]},React.createElement(AccountTheme)))));
 try {
  window.localStorage.setItem('halo-theme-admin:admin%40test.com','dark');
  for(const path of ['/admin','/admin/professor-mode/one','/admin/student-mode/two','/admin/student-preview/three']) {
   await render(path);assert.equal(document.documentElement.dataset.theme,'dark');
   for(const auth of ['/login','/register','/forgot-password','/reset-password','/verify-reset-code']) {
    await render(auth);assert.equal(document.documentElement.dataset.theme,'light');
   }
  }
  await render('/admin');assert.equal(document.documentElement.dataset.theme,'dark');
  await render('/admin',false);assert.equal(document.documentElement.dataset.theme,'light');
  await render('/admin');assert.equal(document.documentElement.dataset.theme,'dark');
 }finally{await act(async()=>root.unmount());host.remove();}
});
