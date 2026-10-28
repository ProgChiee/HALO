import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';
import { validateAcademicField } from '../src/utils/registration.js';

let dom,server,createRoot,Router,AuthContext,ToastContext,Workflow,Profile,api;
const h=React.createElement;
before(async()=>{
 dom=new JSDOM('<html><body></body></html>',{url:'http://localhost'});
 for(const key of ['window','document','HTMLElement','Event','localStorage','sessionStorage'])globalThis[key]=dom.window[key];
 globalThis.IS_REACT_ACT_ENVIRONMENT=true;
 ({createRoot}=await import('react-dom/client'));Router=await import('react-router-dom');
 server=await createServer({server:{middlewareMode:true},appType:'custom'});
 Profile=(await server.ssrLoadModule('/src/pages/student/components/Profile.jsx')).default;
 ({AuthContext}=await server.ssrLoadModule('/src/context/login/useAuth.js'));
 ({ToastContext}=await server.ssrLoadModule('/src/context/notifications/useToast.js'));
 ({StudentWorkflowContext:Workflow}=await server.ssrLoadModule('/src/context/student/useStudentWorkflow.js'));
 api=(await server.ssrLoadModule('/src/services/apiClient.js')).default;
});
after(async()=>{await server?.close();dom?.window.close();});
const original=()=>({userId:12,name:'Own student',email:'synthetic@example.test',studentId:'29AHRT-0001',section:'BSHM 1A',yearLevel:'FIRST_YEAR',profileIncomplete:false});
async function mount(record,{acting=false,preview=false}={}){
 const host=document.createElement('div');document.body.append(host);const root=createRoot(host);const feedback=[];
 const workflow={basePath:acting?'/admin/student-mode':'/student',identity:'synthetic',acting,preview,target:{name:'Target'},leave(){},api:{getProfileData:async()=>({...record}),getProfilePhoto:async()=>null}};
 await act(async()=>root.render(h(Router.MemoryRouter,null,h(AuthContext.Provider,{value:{user:{name:'Own student'},logout(){}}},h(ToastContext.Provider,{value:{showToast:(...args)=>feedback.push(args)}},h(Workflow.Provider,{value:workflow},h(Profile)))))));
 return {host,feedback,async close(){await act(async()=>root.unmount());host.remove();}};
}
const button=(node,text)=>[...node.querySelectorAll('button')].find(b=>b.textContent.trim()===text);
async function edit(ui,label){await act(async()=>ui.host.querySelector(`[aria-label="Edit ${label}"]`).click());return [...ui.host.querySelectorAll('form')].find(form=>form.previousElementSibling.textContent===label);}
async function input(form,value){const el=form.querySelector('input,select');await act(async()=>{Object.getOwnPropertyDescriptor(el.tagName==='SELECT'?window.HTMLSelectElement.prototype:window.HTMLInputElement.prototype,'value').set.call(el,value);el.dispatchEvent(new Event(el.tagName==='SELECT'?'change':'input',{bubbles:true}));});}
async function submit(form){await act(async()=>form.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true})));}
function adapter(record,calls){api.defaults.adapter=async config=>{
 calls.push(config);const data=JSON.parse(config.data),field={STUDENT_ID:'studentId',SECTION:'section',YEAR_LEVEL:'yearLevel'}[data.field];record[field]=data.value;
 return {status:200,headers:{},config,data:{...record}};
};}
test('all three inline fields save only the chosen value and persist on a fresh Profile load',async()=>{
 const record=original(),calls=[];adapter(record,calls);const ui=await mount(record);
 try{
  for(const [label,value,expected] of [['Student ID',' 29AHRT-0123 ','29AHRT-0123'],['Section','  BSHM   2B ','BSHM 2B'],['Year Level','SECOND_YEAR','2nd Year']]){
   const form=await edit(ui,label);
   if(label==='Year Level')assert.deepEqual([...form.querySelectorAll('option')].map(o=>o.value),['','FIRST_YEAR','SECOND_YEAR']);
   await input(form,value);await submit(form);assert.match(ui.host.textContent,new RegExp(expected));
   assert.ok(ui.feedback.some(([message])=>message===`${label} updated successfully.`));
  }
  assert.equal(calls.length,3);
  for(const call of calls){assert.equal(call.method,'patch');assert.equal(call.url,'/student/profile/academic');assert.deepEqual(Object.keys(JSON.parse(call.data)).sort(),['field','value']);}
  assert.equal(record.userId,12);assert.equal(record.email,'synthetic@example.test');
 }finally{await ui.close();}
 const refreshed=await mount(record);try{assert.match(refreshed.host.textContent,/29AHRT-0123/);assert.match(refreshed.host.textContent,/BSHM 2B/);assert.match(refreshed.host.textContent,/2nd Year/);}finally{await refreshed.close();}
});
test('Cancel restores the original value and focus without any request',async()=>{
 const record=original(),calls=[];adapter(record,calls);const ui=await mount(record);
 try{const form=await edit(ui,'Student ID');await input(form,'29AHRT-0999');await act(async()=>button(form,'Cancel').click());
  assert.match(ui.host.textContent,/29AHRT-0001/);assert.doesNotMatch(ui.host.textContent,/29AHRT-0999/);assert.equal(calls.length,0);assert.equal(document.activeElement.getAttribute('aria-label'),'Edit Student ID');
 }finally{await ui.close();}
});
test('invalid values show inline guidance without saving; validation matches registration',async()=>{
 const record=original(),calls=[];adapter(record,calls);const ui=await mount(record);
 try{
  for(const [label,value] of [['Student ID','2023-00123'],['Section','<script>'],['Year Level','']]){
   const form=await edit(ui,label);await input(form,value);await submit(form);assert.ok(form.querySelector('[role="alert"]'));assert.equal(form.querySelector('input,select').getAttribute('aria-invalid'),'true');await act(async()=>button(form,'Cancel').click());
  }assert.equal(calls.length,0);
  assert.ok(validateAcademicField('section',' ').error);assert.ok(validateAcademicField('section','A'.repeat(51)).error);assert.ok(validateAcademicField('yearLevel','THIRD_YEAR').error);
 }finally{await ui.close();}
});
test('pending saves block duplicates and cancellation; duplicate ID errors keep the edit for safe retry',async()=>{
 const record=original(),calls=[];let resolve,reject;api.defaults.adapter=config=>{calls.push(config);return new Promise((yes,no)=>{resolve=yes;reject=no;});};
 const ui=await mount(record);try{
  const form=await edit(ui,'Student ID');await input(form,'29AHRT-0123');await submit(form);await submit(form);
  assert.equal(calls.length,1);assert.ok(button(form,'Cancel').disabled);assert.ok(button(form,'Saving...').disabled);
  await act(async()=>reject({response:{status:409,data:{code:'STUDENT_ID_CONFLICT',message:'private SQL'}}}));
  assert.match(form.textContent,/This Student ID is already registered/);assert.doesNotMatch(form.textContent,/private SQL/);assert.equal(form.querySelector('input').value,'29AHRT-0123');
  await submit(form);await act(async()=>resolve({status:200,headers:{},config:calls[1],data:{...record,studentId:'29AHRT-0123'}}));
  assert.match(ui.host.textContent,/29AHRT-0123/);assert.equal(ui.host.querySelector('form'),null);
 }finally{await ui.close();}
});
test('independent field responses cannot roll back a more recently displayed field',async()=>{
 const record=original(),pending=[];api.defaults.adapter=config=>new Promise(resolve=>pending.push({config,resolve}));const ui=await mount(record);
 try{
  const section=await edit(ui,'Section');await input(section,'BSHM 2C');await submit(section);
  const year=await edit(ui,'Year Level');await input(year,'SECOND_YEAR');await submit(year);
  await act(async()=>pending[1].resolve({status:200,headers:{},config:pending[1].config,data:{...record,section:'BSHM 2C',yearLevel:'SECOND_YEAR'}}));
  await act(async()=>pending[0].resolve({status:200,headers:{},config:pending[0].config,data:{...record,section:'BSHM 2C'}}));
  assert.match(ui.host.textContent,/BSHM 2C/);assert.match(ui.host.textContent,/2nd Year/);
 }finally{await ui.close();}
});
test('Preview and Support profiles are read-only and incomplete accounts retain Admin recovery guidance',async()=>{
 for(const options of [{acting:true,preview:true},{acting:true,preview:false},{acting:false,incomplete:true}]){
  let writes=0;api.defaults.adapter=async()=>{writes++;throw Error('Unexpected write');};
  const record=options.incomplete?{...original(),profileIncomplete:true,studentId:null,section:null,yearLevel:null}:original();
  const ui=await mount(record,options);try{
   for(const label of ['Student ID','Section','Year Level'])assert.equal(ui.host.querySelector(`[aria-label="Edit ${label}"]`),null);
   assert.equal(writes,0);if(options.incomplete)assert.match(ui.host.textContent,/administrator/i);
  }finally{await ui.close();}
 }
});
