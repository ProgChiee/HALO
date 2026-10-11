import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';
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


const deferred = () => { let resolve, reject; const promise = new Promise((a,b) => { resolve=a; reject=b; }); return { promise, resolve, reject }; };
const subject = { id: 7, subjectCode: 'S1', subjectName: 'Hospitality', yearLevel: 'FIRST_YEAR' };
const button = (host, text) => [...host.querySelectorAll('button')].find(b => b.textContent.trim() === text);
async function mount() {
 const Component = (await server.ssrLoadModule('/src/pages/professor/components/SubjectManagement.jsx')).default;
 const host=document.createElement('div');document.body.append(host);const root=createRoot(host);const feedback=[];
 await act(async()=>root.render(React.createElement(AuthContext.Provider,{value:{user:{name:'Professor'},logout(){}}},React.createElement(ToastContext.Provider,{value:{showToast:(...args)=>feedback.push(args)}},React.createElement(Router.MemoryRouter,{},React.createElement(Component))))));
 return {host,feedback,close:async()=>{await act(async()=>root.unmount());host.remove();}};
}
async function fill(host, values) {
 for(const [i,value] of values.entries())await act(async()=>{const input=document.querySelectorAll('form input')[i];Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value').set.call(input,value);input.dispatchEvent(new Event('input',{bubbles:true}));});
}
async function open(host, mode) {
 if(mode==='create')await act(async()=>button(host,'Create Subject').click());
 else if(mode==='edit')await act(async()=>host.querySelector('[aria-label="Edit subject"]').click());
 else {await act(async()=>[...host.querySelectorAll('button')].find(b=>b.textContent.includes('S1')).click());await act(async()=>button(host,'Add New Module').click());}
 await fill(host,mode==='week'?['1','Guest relations']:['S2','Updated subject']);
}
async function submitTwice(host) {await act(async()=>{const form=document.querySelector('form');for(let i=0;i<2;i++)form.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));});}

test('Subject create/edit and Add Week lock dismiss/replacement and duplicate submissions, preserving failed forms',async()=>{
 for(const mode of ['create','edit','week'])for(const outcome of ['success','failure']){
  const pending=deferred();let mutations=0;
  api.defaults.adapter=async config=>{if(config.method!=='get'){mutations++;await pending.promise;}return {status:200,headers:{},config,data:config.url.endsWith('/subjects')?[subject]:[]};};
  const page=await mount();const {host}=page;
  try{
   await open(host,mode);const title=document.querySelector('h3').textContent;
   await submitTwice(host);assert.equal(mutations,1);
   const close=document.querySelector('[aria-label="Close"]');assert.equal(close.disabled,true);assert.equal(document.querySelector('form button[type="submit"]').disabled,true);
   await act(async()=>{close.click();close.parentElement.parentElement.parentElement.click();button(host,'Create Subject').click();host.querySelector('[aria-label="Edit subject"]').click();host.querySelector('[aria-label="Delete subject"]').click();});
   await act(async()=>document.querySelector('[role="dialog"]').dispatchEvent(new dom.window.KeyboardEvent('keydown',{key:'Escape',bubbles:true})));
   assert.equal(document.querySelector('[role="dialog"]').getAttribute('aria-busy'),'true');
   assert.equal(document.querySelector('h3').textContent,title);assert.equal(document.querySelectorAll('form').length,1);
   await act(async()=>outcome==='success'?pending.resolve():pending.reject({response:{status:409,data:{code:'WEEK_NUMBER_ALREADY_EXISTS'}}}));
   if(outcome==='success'){assert.equal(document.querySelector('form'),null);assert.equal(page.feedback.length,1);}
   else {assert.equal(document.querySelector('h3').textContent,title);assert.equal(document.querySelector('form input').value,mode==='week'?'1':'S2');assert.match(document.body.textContent,/already has that week number/);assert.equal(close.disabled,false);await act(async()=>close.click());}
   await act(async()=>button(host,'Create Subject').click());assert.equal(document.querySelector('h3').textContent,'Create Subject');assert.doesNotMatch(document.querySelector('form').textContent,/already has that week number/);
  }finally{await page.close();}
 }
});

test('late success/error after unmount cannot affect a new modal or emit feedback',async()=>{
 for(const mode of ['create','edit','week'])for(const outcome of ['success','failure']){
  const pending=deferred();let mutations=0;
  api.defaults.adapter=async config=>{if(config.method!=='get'){mutations++;await pending.promise;}return {status:200,headers:{},config,data:config.url.endsWith('/subjects')?[subject]:[]};};
  const old=await mount();await open(old.host,mode);await submitTwice(old.host);await old.close();
  const current=await mount();
  try {await open(current.host,'edit');await act(async()=>outcome==='success'?pending.resolve():pending.reject({response:{status:409,data:{code:'SUBJECT_CODE_ALREADY_EXISTS'}}}));assert.equal(mutations,1);assert.equal(old.feedback.length,0);assert.equal(document.querySelector('h3').textContent,'Edit Subject');assert.equal(document.querySelector('form input').value,'S2');assert.doesNotMatch(current.host.textContent,/already used/);}finally{await current.close();}
 }
});

test('academic dialogs have names, associated labels, initial focus and Escape returns focus',async()=>{
 api.defaults.adapter=async config=>({status:200,headers:{},config,data:config.url.endsWith('/subjects')?[subject]:[]});
 const page=await mount();
 try {
  for(const label of ['Create Subject','Edit subject','Delete subject']){
   const trigger=label==='Create Subject'?button(page.host,label):page.host.querySelector('[aria-label="'+label+'"]');trigger.focus();
   await act(async()=>trigger.click());
   const dialog=document.querySelector('[role="dialog"]');assert.ok(dialog);assert.equal(dialog.getAttribute('aria-modal'),'true');assert.ok(document.getElementById(dialog.getAttribute('aria-labelledby')).textContent);
   assert.ok(dialog.contains(document.activeElement));
   const lastAction = [...dialog.querySelectorAll('button')].at(-1);
   let reachedAction = document.activeElement === lastAction;
   for(let i=0;i<12 && !reachedAction;i++) {
    await act(async()=>dialog.dispatchEvent(new dom.window.KeyboardEvent('keydown',{key:'Tab',bubbles:true})));
    reachedAction = document.activeElement === lastAction;
   }
   assert.ok(reachedAction, 'bottom action remains in the dialog keyboard cycle');
   for(const field of dialog.querySelectorAll('input,select,textarea'))assert.ok(field.labels.length>0);
   await act(async()=>dialog.dispatchEvent(new dom.window.KeyboardEvent('keydown',{key:'Escape',bubbles:true})));
   assert.equal(document.querySelector('[role="dialog"]'),null);assert.equal(document.activeElement,trigger);
  }
  await act(async()=>[...page.host.querySelectorAll('button')].find(b=>b.textContent.includes('S1')).click());
  const trigger=button(page.host,'Add New Module');trigger.focus();await act(async()=>trigger.click());
  const dialog=document.querySelector('[role="dialog"]');assert.equal(document.activeElement.id,'week-number');
  for(const field of dialog.querySelectorAll('input'))assert.ok(field.labels.length);
  await act(async()=>dialog.dispatchEvent(new dom.window.KeyboardEvent('keydown',{key:'Escape',bubbles:true})));
  assert.equal(document.activeElement,trigger);
 }finally{await page.close();}
});

test('module title edit saves existing week, validates, cancels, and survives reload', async()=>{
 let week={id:37,weekNumber:1,title:'Original'};let writes=0;const pending=deferred();
 api.defaults.adapter=async config=>{
  if(config.method==='put'){writes++;assert.equal(config.url,'/professor/weeks/37');const payload=JSON.parse(config.data);assert.equal(payload.weekNumber,1);await pending.promise;week={...week,...payload};return {status:200,headers:{},config,data:week};}
  return {status:200,headers:{},config,data:config.url.endsWith('/subjects')?[subject]:[week]};
 };
 let page=await mount();
 const expand=async()=>act(async()=>[...page.host.querySelectorAll('button')].find(b=>b.textContent.includes('S1')).click());
 try{
  await expand();await act(async()=>button(page.host,'Edit title').click());
  await fill(page.host,['   ']);await submitTwice(page.host);assert.equal(writes,0);assert.match(document.body.textContent,/between 1 and 255/);
  await act(async()=>button(document,'Cancel').click());assert.equal(document.querySelector('[role="dialog"]'),null);assert.match(page.host.textContent,/Original/);
  await act(async()=>button(page.host,'Edit title').click());await fill(page.host,['  Guest Services  ']);await submitTwice(page.host);assert.equal(writes,1);
  assert.equal(button(document,'Cancel').disabled,true);assert.equal(document.querySelector('[aria-label="Close"]').disabled,true);
  await act(async()=>pending.resolve());assert.equal(document.querySelector('[role="dialog"]'),null);assert.match(page.host.textContent,/Week 1: Guest Services/);
  await page.close();page=await mount();await expand();assert.match(page.host.textContent,/Week 1: Guest Services/);assert.equal(writes,1);
 }finally{await page.close();}
});

test('module title save failure preserves draft and can be retried',async()=>{
 api.defaults.adapter=async config=>{if(config.method==='put')throw {response:{status:403}};return {status:200,headers:{},config,data:config.url.endsWith('/subjects')?[subject]:[{id:37,weekNumber:1,title:'Original'}]};};
 const page=await mount();try{
  await act(async()=>[...page.host.querySelectorAll('button')].find(b=>b.textContent.includes('S1')).click());await act(async()=>button(page.host,'Edit title').click());await fill(page.host,['Updated']);await submitTwice(page.host);
  assert.equal(document.querySelector('#module-title').value,'Updated');assert.ok(document.querySelector('[role="alert"]'));assert.equal(button(document,'Save').disabled,false);assert.match(page.host.textContent,/Original/);
 }finally{await page.close();}
});
