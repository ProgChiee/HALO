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
