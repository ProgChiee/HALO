import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';
let dom,server,createRoot,Router,AuthContext,Sidebar,Dialog;
before(async()=>{
 dom=new JSDOM('<html><body></body></html>',{url:'http://localhost'});
 for(const k of ['window','document','HTMLElement','Event'])globalThis[k]=dom.window[k];
 globalThis.IS_REACT_ACT_ENVIRONMENT=true;
 ({createRoot}=await import('react-dom/client'));Router=await import('react-router-dom');
 server=await createServer({server:{middlewareMode:true},appType:'custom'});
 ({AuthContext}=await server.ssrLoadModule('/src/context/login/useAuth.js'));
 Sidebar=(await server.ssrLoadModule('/src/components/shared/Sidebar.jsx')).default;
 Dialog=(await server.ssrLoadModule('/src/components/shared/Dialog.jsx')).default;
});
after(async()=>{await server.close();dom.window.close();});
test('shared logout confirms for all roles and acting paths; Cancel/Escape restore focus; duplicate submission blocked',async()=>{
 for(const [role,path] of [['superadmin','/superadmin'],['admin','/admin'],['professor','/professor'],['student','/student'],['admin','/admin/professor-mode/1'],['admin','/admin/student-preview/2'],['admin','/admin/student-mode/3']]) {
  let calls=0,finish;const logout=()=>{calls++;return new Promise(resolve=>{finish=resolve;});};
  const host=document.createElement('div');document.body.append(host);const root=createRoot(host);
  function Location(){return React.createElement('output',null,Router.useLocation().pathname);}
  try {
   await act(async()=>root.render(React.createElement(AuthContext.Provider,{value:{user:{name:'Test'},role,logout}},React.createElement(Router.MemoryRouter,{initialEntries:[path]},React.createElement(Sidebar),React.createElement(Location)))));
   const opener=host.querySelector('button[aria-label="Log out"]');
   await act(async()=>opener.click());assert.equal(calls,0);
   let dialog=document.querySelector('[role="dialog"]');assert.match(dialog.textContent,/Log out of HALO\?/);assert.equal(document.activeElement.textContent,'Cancel');
   await act(async()=>dialog.querySelector('button').click());assert.equal(document.querySelector('[role="dialog"]'),null);assert.equal(document.activeElement,opener);
   await act(async()=>opener.click());dialog=document.querySelector('[role="dialog"]');
   await act(async()=>dialog.dispatchEvent(new window.KeyboardEvent('keydown',{key:'Escape',bubbles:true})));assert.equal(calls,0);assert.equal(document.activeElement,opener);
   await act(async()=>opener.click());dialog=document.querySelector('[role="dialog"]');const confirm=dialog.querySelectorAll('button')[1];
   await act(async()=>{confirm.click();confirm.click();});assert.equal(calls,1);assert.equal(confirm.disabled,true);
   await act(async()=>dialog.dispatchEvent(new window.KeyboardEvent('keydown',{key:'Escape',bubbles:true})));assert.ok(document.querySelector('[role="dialog"]'));
   await act(async()=>finish());assert.equal(host.querySelector('output').textContent,'/login');assert.equal(document.querySelector('[role="dialog"]'),null);
  }finally{await act(async()=>root.unmount());host.remove();}
 }
});
test('confirmation works inside mobile navigation dialog without competing focus traps',async()=>{
 const host=document.createElement('div');document.body.append(host);const root=createRoot(host);
 try {
  await act(async()=>root.render(React.createElement(AuthContext.Provider,{value:{user:{name:'Test'},logout(){}}},React.createElement(Router.MemoryRouter,null,React.createElement(Dialog,{labelledBy:'nav-title',onClose(){},initialFocus:'button'},React.createElement('h2',{id:'nav-title'},'Navigation'),React.createElement(Sidebar))))));
  const opener=document.querySelector('button[aria-label="Log out"]');await act(async()=>opener.click());
  const dialogs=document.querySelectorAll('[role="dialog"]');assert.equal(dialogs.length,2);assert.equal(document.activeElement.textContent,'Cancel');
  await act(async()=>document.activeElement.dispatchEvent(new window.KeyboardEvent('keydown',{key:'Tab',bubbles:true})));assert.equal(document.activeElement.textContent,'Log Out');
  await act(async()=>dialogs[1].querySelector('button').click());assert.equal(document.activeElement,opener);assert.equal(document.querySelectorAll('[role="dialog"]').length,1);
 }finally{await act(async()=>root.unmount());host.remove();}
});

test('real logout waits for server revocation, preserves session on failure, and deduplicates retry',async()=>{
 globalThis.localStorage=window.localStorage;globalThis.sessionStorage=window.sessionStorage;
 const {AuthProvider}=await server.ssrLoadModule('/src/context/login/AuthContext.jsx');
 const {useAuth}=await server.ssrLoadModule('/src/context/login/useAuth.js');
 const {writeSession,readSession}=await server.ssrLoadModule('/src/utils/session.js');
 const api=(await server.ssrLoadModule('/src/services/apiClient.js')).default;
 writeSession({id:20,name:'Admin',email:'admin@example.test',role:'admin'},'actor-token',false);localStorage.setItem('halo-theme-20','dark');
 let offline=true;let resets=0;window.haloTheme={bindAccount(value){if(value===null)resets++;}};const calls=[];
 api.defaults.adapter=async config=>{calls.push(config);if(config.url==='/auth/logout' && offline)throw new Error('offline');return {config,status:204,data:null,headers:{}};};
 let logout;function Consumer(){logout=useAuth().logout;return null;}
 const host=document.createElement('div');document.body.append(host);const root=createRoot(host);
 try{await act(async()=>root.render(React.createElement(AuthProvider,null,React.createElement(Consumer))));
 await act(async()=>{const first=logout();assert.equal(logout(),first);await assert.rejects(first,/offline/);});
 assert.equal(readSession().token,'actor-token');assert.equal(resets,0);
 offline=false;
 await act(async()=>{const first=logout();assert.equal(logout(),first);assert.equal(readSession().token,'actor-token');await first;});
 assert.equal(readSession().token,null);
 const events=calls.filter(c=>c.url==='/auth/logout');assert.equal(events.length,2);assert.equal(events[0].headers.get('Authorization'),'Bearer actor-token');assert.equal(events[0].data,undefined);
 assert.ok(resets>=1);assert.equal(localStorage.getItem('halo-theme-20'),'dark');
 }finally{await act(async()=>root.unmount());host.remove();delete window.haloTheme;}
});
test('logout server failure retains confirmation and allows retry without navigating',async()=>{
 let calls=0;const logout=async()=>{if(++calls===1)throw new Error('offline');};
 const host=document.createElement('div');document.body.append(host);const root=createRoot(host);
 function Location(){return React.createElement('output',{},Router.useLocation().pathname);}
 try {
  await act(async()=>root.render(React.createElement(AuthContext.Provider,{value:{user:{name:'Admin'},role:'admin',logout}},React.createElement(Router.MemoryRouter,{initialEntries:['/admin']},React.createElement(Sidebar),React.createElement(Location)))));
  await act(async()=>document.querySelector('[aria-label="Log out"]').click());
  const confirm=()=>[...document.querySelectorAll('[role="dialog"] button')].find(b=>b.textContent==='Log Out');
  await act(async()=>confirm().click());
  assert.equal(document.querySelector('output').textContent,'/admin');
  assert.match(document.querySelector('[role="dialog"] [role="alert"]').textContent,/Could not confirm logout/);
  await act(async()=>confirm().click());
  assert.equal(calls,2);assert.equal(document.querySelector('[role="dialog"]'),null);
  assert.equal(document.querySelector('output').textContent,'/login');
 }finally{await act(async()=>root.unmount());host.remove();}
});
