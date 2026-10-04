import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';
let dom, server, createRoot, Router, AuthContext, ToastContext, Editor, api;
before(async () => {
 dom=new JSDOM('<html><body></body></html>',{url:'http://localhost'});
 for(const key of ['window','document','HTMLElement','Event','localStorage','sessionStorage']) globalThis[key]=dom.window[key];
 globalThis.IS_REACT_ACT_ENVIRONMENT=true;
 ({createRoot}=await import('react-dom/client'));Router=await import('react-router-dom');
 server=await createServer({server:{middlewareMode:true},appType:'custom'});
 ({AuthContext}=await server.ssrLoadModule('/src/context/login/useAuth.js'));
 ({ToastContext}=await server.ssrLoadModule('/src/context/notifications/useToast.js'));
 Editor=(await server.ssrLoadModule('/src/pages/professor/components/LessonEditor.jsx')).default;
 api=(await server.ssrLoadModule('/src/services/apiClient.js')).default;
});
after(async()=>{await server?.close();dom?.window.close();});
const moduleData=(id,text)=>({id,lessonText:text,files:[],status:'PENDING',aiGenerationStatus:'PENDING'});
async function fixture(run) {
 const host=document.createElement('div');document.body.append(host);const root=createRoot(host);const old=api.defaults.adapter;
 const pending=[],toasts=[];let navigate;
 api.defaults.adapter=config=>new Promise((resolve,reject)=>pending.push({config,resolve:data=>resolve({data,status:200,headers:{},config}),reject}));
 function Nav(){navigate=Router.useNavigate();return null;}
 try {
  await act(async()=>root.render(React.createElement(AuthContext.Provider,{value:{user:{name:'Professor'},logout(){}}},React.createElement(ToastContext.Provider,{value:{showToast:(...args)=>toasts.push(args)}},React.createElement(Router.MemoryRouter,{initialEntries:['/professor/subjects/1/week/10']},React.createElement(Nav),React.createElement(Router.Routes,{},React.createElement(Router.Route,{path:'/professor/subjects/:subjectId/week/:weekId',element:React.createElement(Editor)})))))));
  await run({host,pending,toasts,go:async path=>act(async()=>navigate(path)),resolve:async(index,data)=>act(async()=>pending[index].resolve(data)),root});
 }finally{await act(async()=>root.unmount());host.remove();api.defaults.adapter=old;}
}
test('route change immediately removes old materials/actions; late GET cannot overwrite newer route',async()=>fixture(async({host,pending,go,resolve})=>{
 await resolve(0,moduleData(1,'Week A'));
 assert.equal(host.querySelector('textarea').value,'Week A');
 await go('/professor/subjects/1/week/20');
 assert.match(host.textContent,/Loading saved lesson/);assert.equal(host.querySelector('textarea'),null);
 assert.ok(!host.textContent.includes('Generate Lesson with AI'));
 await go('/professor/subjects/1/week/30');
 await resolve(2,moduleData(3,'Week C'));
 await resolve(1,moduleData(2,'Week B delayed'));
 assert.equal(host.querySelector('textarea').value,'Week C');
 assert.equal(pending[1].config.signal.aborted,true);
 // Subject-only changes must also discard all state, even with the same week ID.
 await go('/professor/subjects/2/week/30');assert.equal(host.querySelector('textarea'),null);
 await resolve(3,moduleData(3,'New subject route'));assert.equal(host.querySelector('textarea').value,'New subject route');
}));
test('late successful and failed mutations cannot update new route or show stale toasts',async()=>{
 for(const fail of [false,true]) await fixture(async({host,pending,toasts,go,resolve})=>{
  await resolve(0,moduleData(1,'Week A'));
  await act(async()=>[...host.querySelectorAll('button')].find(b=>b.textContent.includes('Generate Lesson with AI')).click());
  assert.match(pending[1].config.url,/\/1\/generate$/);
  await go('/professor/subjects/1/week/20');assert.equal(host.querySelector('textarea'),null);
  await resolve(2,moduleData(2,'Week B'));
  if(fail) await act(async()=>pending[1].reject({response:{status:500}}));
  else await resolve(1,{...moduleData(1,'Week A'),aiGenerationStatus:'COMPLETED',generatedObjectives:'STALE OBJECTIVES'});
  assert.equal(host.querySelector('textarea').value,'Week B');assert.ok(!host.textContent.includes('STALE OBJECTIVES'));assert.deepEqual(toasts,[]);
  assert.equal(pending.length,3); // Failed old generation must not start a refresh.
  await act(async()=>[...host.querySelectorAll('button')].find(b=>b.textContent.includes('Generate Lesson with AI')).click());
  await resolve(3,{...moduleData(2,'Week B'),aiGenerationStatus:'COMPLETED',generatedObjectives:'Current objectives'});
  assert.match(host.textContent,/Current objectives/);assert.equal(toasts.length,1);
 });
});

test('delayed save cannot replace new module and unmounted generation has no UI effects',async()=>fixture(async({host,pending,toasts,go,resolve})=>{
 await resolve(0,moduleData(1,'Week A'));
 await act(async()=>{
  const input=host.querySelector('textarea');
  Object.getOwnPropertyDescriptor(dom.window.HTMLTextAreaElement.prototype,'value').set.call(input,'Edited A');
  input.dispatchEvent(new dom.window.Event('input',{bubbles:true}));
 });
 await act(async()=>[...host.querySelectorAll('button')].find(b=>b.textContent==='Save Materials').click());
 assert.equal(pending[1].config.method,'put');
 await go('/professor/subjects/1/week/20');await resolve(2,moduleData(2,'Week B'));
 await resolve(1,moduleData(1,'Edited A'));assert.equal(host.querySelector('textarea').value,'Week B');assert.deepEqual(toasts,[]);
 await act(async()=>[...host.querySelectorAll('button')].find(b=>b.textContent.includes('Generate Lesson with AI')).click());
 await go('/outside-editor');
 await resolve(3,{...moduleData(2,'Week B'),aiGenerationStatus:'COMPLETED'});
 assert.equal(host.querySelector('textarea'),null);assert.deepEqual(toasts,[]);
}));

const ready = () => ({...moduleData(1,'Lesson'),aiGenerationStatus:'COMPLETED',generatedObjectives:'Objectives'});
const approveButton = host => [...host.querySelectorAll('button')].find(b=>b.textContent.includes('Approve & Publish'));
test('approval uses bounded extended timeout and prevents duplicate submissions',async()=>fixture(async({host,pending,toasts,resolve})=>{
 await resolve(0,ready());
 const button=approveButton(host);
 await act(async()=>{button.click();button.click();});
 assert.equal(pending.length,2);assert.equal(pending[1].config.method,'put');assert.equal(pending[1].config.timeout,180000);
 assert.ok(approveButton(host)===undefined || approveButton(host).disabled);
 await resolve(1,{...ready(),status:'APPROVED'});
 assert.match(host.textContent,/Published: this lesson/);assert.equal(toasts[0][1],'success');
}));
test('uncertain approval and conflicts reconcile approved or unpublished state without retrying mutation',async()=>{
 for(const error of [{code:'ECONNABORTED'},{code:'ERR_NETWORK'},{response:{status:409,data:{code:'STALE_MODULE_OPERATION'}}}]) {
  for(const approved of [true,false]) await fixture(async({host,pending,toasts,resolve})=>{
   await resolve(0,ready());await act(async()=>approveButton(host).click());
   await act(async()=>pending[1].reject(error));
   assert.equal(pending[2].config.method,'get');assert.match(pending[2].config.url,/week\/10$/);
   await resolve(2,{...ready(),status:approved?'APPROVED':'PENDING'});
   assert.equal(pending.filter(r=>r.config.method==='put').length,1);
   assert.equal(toasts.at(-1)[1],approved?'success':'error');
   if(approved) assert.match(host.textContent,/Published: this lesson/);
   else {assert.match(toasts.at(-1)[0],/reload its status before trying again/);assert.equal(approveButton(host).disabled,false);}
  });
 }
});
test('failed reconciliation locks editing behind retry loading and stale reconciliation cannot change another week',async()=>{
 await fixture(async({host,pending,resolve})=>{
  await resolve(0,ready());await act(async()=>approveButton(host).click());
  await act(async()=>pending[1].reject({code:'ECONNABORTED'}));
  await act(async()=>pending[2].reject({code:'ERR_NETWORK'}));
  assert.match(host.textContent,/Could not verify whether publishing completed/);assert.equal(approveButton(host),undefined);assert.equal(pending.length,3);
 });
 await fixture(async({host,pending,toasts,resolve,go})=>{
  await resolve(0,ready());await act(async()=>approveButton(host).click());await act(async()=>pending[1].reject({code:'ECONNABORTED'}));
  await go('/professor/subjects/1/week/20');await resolve(3,moduleData(2,'Week B'));
  await resolve(2,{...ready(),status:'APPROVED'});
  assert.equal(host.querySelector('textarea').value,'Week B');assert.deepEqual(toasts,[]);
 });
});

test('publication material errors show actionable messages without retrying approval or hiding them behind reconciliation',async()=>{
 for(const [code,status,expected] of [
  ['MODULE_MATERIALS_REQUIRED',409,/Upload at least one original lesson file/],
  ['MODULE_MATERIAL_UNREADABLE',409,/Replace it with a readable file/],
  ['MODULE_INDEX_UNAVAILABLE',502,/material preparation is unavailable/],
 ]) await fixture(async({host,pending,toasts,resolve})=>{
  await resolve(0,ready());await act(async()=>approveButton(host).click());
  await act(async()=>pending[1].reject({response:{status,data:{code,message:'INTERNAL MESSAGE MUST NOT DISPLAY'}}}));
  assert.match(toasts.at(-1)[0],expected);assert.equal(toasts.at(-1)[1],'error');
  assert.equal(pending.length,2);assert.equal(approveButton(host).disabled,false);
  assert.ok(!host.textContent.includes('Published: this lesson'));
 });
});
