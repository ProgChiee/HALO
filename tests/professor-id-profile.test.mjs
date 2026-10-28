import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';
import { validateProfessorId } from '../src/utils/adminProfessorValidation.js';

let dom,server,createRoot,Router,AuthContext,ToastContext,Workflow,Profile,api;
const h=React.createElement;
before(async()=>{
 dom=new JSDOM('<html><body></body></html>',{url:'http://localhost'});
 for(const key of ['window','document','HTMLElement','Event','localStorage','sessionStorage'])globalThis[key]=dom.window[key];
 globalThis.IS_REACT_ACT_ENVIRONMENT=true;
 ({createRoot}=await import('react-dom/client'));Router=await import('react-router-dom');
 server=await createServer({server:{middlewareMode:true},appType:'custom'});
 Profile=(await server.ssrLoadModule('/src/pages/professor/components/ProfessorProfile.jsx')).default;
 ({AuthContext}=await server.ssrLoadModule('/src/context/login/useAuth.js'));
 ({ToastContext}=await server.ssrLoadModule('/src/context/notifications/useToast.js'));
 ({ProfessorWorkflowContext:Workflow}=await server.ssrLoadModule('/src/context/professor/useProfessorWorkflow.js'));
 api=(await server.ssrLoadModule('/src/services/apiClient.js')).default;
});
after(async()=>{await server?.close();dom?.window.close();});
const original=()=>({userId:12,name:'Own student',email:'synthetic@example.test',professorId:'PROF-0001',section:'BSHM 1A',yearLevel:'FIRST_YEAR',profileIncomplete:false});
async function mount(record,{acting=false,preview=false}={}){
 const host=document.createElement('div');document.body.append(host);const root=createRoot(host);const feedback=[];
 const workflow={basePath:acting?'/admin/student-mode':'/student',identity:'synthetic',acting,preview,target:{name:'Target'},leave(){},api:{getProfessorProfile:async()=>({...record}),getProfilePhoto:async()=>null}};
 await act(async()=>root.render(h(Router.MemoryRouter,null,h(AuthContext.Provider,{value:{user:{name:'Own student'},logout(){}}},h(ToastContext.Provider,{value:{showToast:(...args)=>feedback.push(args)}},h(Workflow.Provider,{value:workflow},h(Profile)))))));
 return {host,feedback,async close(){await act(async()=>root.unmount());host.remove();}};
}
const button=(node,text)=>[...node.querySelectorAll('button')].find(b=>b.textContent.trim()===text);
async function edit(ui,label){await act(async()=>ui.host.querySelector(`[aria-label="Edit ${label}"]`).click());return [...ui.host.querySelectorAll('form')].find(form=>form.previousElementSibling.textContent===label);}
async function input(form,value){const el=form.querySelector('input,select');await act(async()=>{Object.getOwnPropertyDescriptor(el.tagName==='SELECT'?window.HTMLSelectElement.prototype:window.HTMLInputElement.prototype,'value').set.call(el,value);el.dispatchEvent(new Event(el.tagName==='SELECT'?'change':'input',{bubbles:true}));});}
async function submit(form){await act(async()=>form.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true})));}
function adapter(record,calls){api.defaults.adapter=async config=>{if(config.method==='get')return Promise.resolve({status:200,headers:{},config,data:null});calls.push(config);record.professorId=JSON.parse(config.data).professorId;return {status:200,headers:{},config,data:{...record}};};}
test('Professor ID saves trimmed value and refresh retains it',async()=>{
 const record=original(),calls=[];adapter(record,calls);const ui=await mount(record);
 try{const form=await edit(ui,'Professor ID');await input(form,' NEW-123 ');await submit(form);assert.match(ui.host.textContent,/NEW-123/);assert.equal(calls.length,1);assert.equal(calls[0].url,'/professor/profile/id');assert.deepEqual(JSON.parse(calls[0].data),{professorId:'NEW-123'});assert.ok(ui.feedback.length);}finally{await ui.close();}
 const again=await mount(record);try{assert.match(again.host.textContent,/NEW-123/);}finally{await again.close();}
});
test('Cancel restores the original value and focus without any request',async()=>{
 const record=original(),calls=[];adapter(record,calls);const ui=await mount(record);
 try{const form=await edit(ui,'Professor ID');await input(form,'PROF-0999');await act(async()=>button(form,'Cancel').click());
  assert.match(ui.host.textContent,/PROF-0001/);assert.doesNotMatch(ui.host.textContent,/PROF-0999/);assert.equal(calls.length,0);assert.equal(document.activeElement.getAttribute('aria-label'),'Edit Professor ID');
 }finally{await ui.close();}
});
test('existing validation permits arbitrary assigned IDs but rejects blank and overlong values',async()=>{
 assert.equal(validateProfessorId(' INVALID123 ').value,'INVALID123');assert.equal(validateProfessorId('PROF-011').error,'');
 const record=original(),calls=[];adapter(record,calls);const ui=await mount(record);
 try{const form=await edit(ui,'Professor ID');for(const value of ['   ','A'.repeat(256)]){await input(form,value);await submit(form);assert.ok(form.querySelector('[role="alert"]'));}assert.equal(calls.length,0);}finally{await ui.close();}
});
test('pending saves block duplicates and cancellation; duplicate ID errors keep the edit for safe retry',async()=>{
 const record=original(),calls=[];let resolve,reject;api.defaults.adapter=config=>{if(config.method==='get')return Promise.resolve({status:200,headers:{},config,data:null});calls.push(config);return new Promise((yes,no)=>{resolve=yes;reject=no;});};
 const ui=await mount(record);try{
  const form=await edit(ui,'Professor ID');await input(form,'PROF-0123');await submit(form);await submit(form);
  assert.equal(calls.length,1);assert.ok(button(form,'Cancel').disabled);assert.ok(button(form,'Saving...').disabled);
  await act(async()=>reject({response:{status:409,data:{code:'PROFESSOR_ID_CONFLICT',message:'private SQL'}}}));
  assert.match(form.textContent,/This Professor ID is already registered/);assert.doesNotMatch(form.textContent,/private SQL/);assert.equal(form.querySelector('input').value,'PROF-0123');
  await submit(form);await act(async()=>resolve({status:200,headers:{},config:calls[1],data:{...record,professorId:'PROF-0123'}}));
  assert.match(ui.host.textContent,/PROF-0123/);assert.equal(ui.host.querySelector('form'),null);
 }finally{await ui.close();}
});
test('Admin Preview and Acting profiles do not expose the ID editor',async()=>{
 for(const preview of [true,false]){const ui=await mount(original(),{acting:true,preview});try{assert.equal(ui.host.querySelector('[aria-label="Edit Professor ID"]'),null);}finally{await ui.close();}}
});
