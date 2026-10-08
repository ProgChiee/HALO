import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';
let server, dom, createRoot, Page, AuthContext, ToastContext, MemoryRouter, api;
before(async()=>{
 dom=new JSDOM('<html><body></body></html>',{url:'http://localhost'});
 for(const k of ['window','document','HTMLElement','Event','MouseEvent','localStorage','sessionStorage'])globalThis[k]=dom.window[k];
 globalThis.IS_REACT_ACT_ENVIRONMENT=true;
 ({createRoot}=await import('react-dom/client'));({MemoryRouter}=await import('react-router-dom'));
 server=await createServer({server:{middlewareMode:true},appType:'custom'});
 Page=(await server.ssrLoadModule('/src/pages/admin/components/ProfessorManagement.jsx')).default;
 ({AuthContext}=await server.ssrLoadModule('/src/context/login/useAuth.js'));
 ({ToastContext}=await server.ssrLoadModule('/src/context/notifications/useToast.js'));
 api=(await server.ssrLoadModule('/src/services/apiClient.js')).default;
});
after(async()=>{await server.close();dom.window.close();});
const row={id:1,name:'Professor',email:'prof@example.test',professorId:'P1',status:'ACTIVE'};
for(const mode of ['create','edit'])test(`${mode} locks dismissal/replacement and duplicate submits; failure retains form; success closes own modal`,async()=>{
 const host=document.createElement('div');document.body.append(host);const root=createRoot(host);const mutations=[];
 api.defaults.adapter=config=>config.method==='get'?Promise.resolve({config,data:[row],status:200,headers:{}}):new Promise((resolve,reject)=>mutations.push({resolve:data=>resolve({config,data,status:200,headers:{}}),reject}));
 try {
  await act(async()=>root.render(React.createElement(MemoryRouter,{},React.createElement(AuthContext.Provider,{value:{role:'admin',user:{name:'Admin'}}},React.createElement(ToastContext.Provider,{value:{showToast(){}}},React.createElement(Page))))));
  await act(async()=>mode==='create'?[...document.querySelectorAll('button')].find(b=>b.textContent.includes('Create Professor')).click():document.querySelector('[aria-label="Edit"]').click());
  if(mode==='create')for(const [index,value]of ['New Professor','new@example.test','P2','password-123'].entries())await act(async()=>{
   const input=document.querySelectorAll('form input')[index];Object.getOwnPropertyDescriptor(dom.window.HTMLInputElement.prototype,'value').set.call(input,value);input.dispatchEvent(new Event('input',{bubbles:true}));
  });
  const form=document.querySelector('form');
  for(const input of form.querySelectorAll('input')) assert.ok(form.querySelector('label[for="'+input.id+'"]'));
  const submit=()=>form.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));
  await act(async()=>{submit();submit();});assert.equal(mutations.length,1);
  const close=document.querySelector('[aria-label="Close"]');assert.equal(close.disabled,true);
  await act(async()=>{close.click();form.parentElement.parentElement.click();document.querySelector('[aria-label="Edit"]').click();document.querySelector('[aria-label="View"]').click();});
  await act(async()=>document.querySelector('[role="dialog"]').dispatchEvent(new dom.window.KeyboardEvent('keydown',{key:'Escape',bubbles:true})));
  assert.equal(document.querySelector('form'),form);assert.equal(document.querySelectorAll('form').length,1);
  assert.equal(document.querySelector('[aria-label="Edit"]').disabled,true);
  assert.equal([...document.querySelectorAll('button')].find(b=>b.textContent.trim()==='Create Professor').disabled,true);
  await act(async()=>mutations[0].reject({response:{status:409,data:{code:'PROFESSOR_ID_CONFLICT'}}}));
  assert.equal(document.querySelector('form'),form);assert.match(form.textContent,/Professor ID is already in use/);
  assert.equal(form.querySelector('input').value,mode==='create'?'New Professor':'Professor');
  await act(async()=>submit());assert.equal(mutations.length,2);
  await act(async()=>mutations[1].resolve(row));assert.equal(document.querySelector('form'),null);
 }finally{await act(async()=>root.unmount());host.remove();}
});
for(const outcome of ['success','failure'])test(`late ${outcome} from an unmounted form cannot affect a new modal`,async()=>{
 const host=document.createElement('div');document.body.append(host);let root=createRoot(host);let pending;const toasts=[];
 api.defaults.adapter=config=>config.method==='get'?Promise.resolve({config,data:[row],status:200,headers:{}}):new Promise((resolve,reject)=>{pending={resolve:()=>resolve({config,data:row,status:200,headers:{}}),reject};});
 const mount=async()=>act(async()=>root.render(React.createElement(MemoryRouter,{},React.createElement(AuthContext.Provider,{value:{role:'admin',user:{name:'Admin'}}},React.createElement(ToastContext.Provider,{value:{showToast:(...args)=>toasts.push(args)}},React.createElement(Page))))));
 try{
  await mount();await act(async()=>document.querySelector('[aria-label="Edit"]').click());
  await act(async()=>document.querySelector('form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true})));
  await act(async()=>root.unmount());root=createRoot(host);await mount();await act(async()=>document.querySelector('[aria-label="Edit"]').click());
  const form=document.querySelector('form');
  await act(async()=>outcome==='success'?pending.resolve():pending.reject({response:{status:409,data:{code:'PROFESSOR_ID_CONFLICT'}}}));
  assert.equal(document.querySelector('form'),form);assert.doesNotMatch(form.textContent,/already in use/);assert.deepEqual(toasts,[]);
 }finally{await act(async()=>root.unmount());host.remove();}
});

for (const mode of ['create', 'edit']) test('AD-12 '+mode+' refresh preserves newer status and refreshed fields', async () => {
 const host=document.createElement('div');document.body.append(host);const root=createRoot(host);const calls=[];
 api.defaults.adapter=config=>new Promise(resolve=>calls.push({config,resolve:data=>resolve({config,data,status:200,headers:{}})}));
 const click=async selector=>act(async()=>document.querySelector(selector).click());
 try {
  await act(async()=>root.render(React.createElement(MemoryRouter,{},React.createElement(AuthContext.Provider,{value:{role:'admin',user:{name:'Admin'}}},React.createElement(ToastContext.Provider,{value:{showToast(){}}},React.createElement(Page))))));
  await act(async()=>calls[0].resolve([row]));
  await click('[aria-label="Deactivate"]');
  assert.deepEqual(JSON.parse(calls[1].config.data),{status:'INACTIVE'});
  if(mode==='edit') await click('[aria-label="Edit"]');
  else {
   await act(async()=>[...document.querySelectorAll('button')].find(b=>b.textContent.includes('Create Professor')).click());
   for(const [index,value]of ['New Professor','new@example.test','P2','password-123'].entries()) await act(async()=>{
    const input=document.querySelectorAll('form input')[index];Object.getOwnPropertyDescriptor(dom.window.HTMLInputElement.prototype,'value').set.call(input,value);input.dispatchEvent(new Event('input',{bubbles:true}));
   });
  }
  await act(async()=>document.querySelector('form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true})));
  await act(async()=>calls[2].resolve(row));
  assert.equal(calls[3].config.method,'get');assert.ok(calls[3].config.signal);
  await act(async()=>calls[1].resolve({...row,status:'INACTIVE'}));
  await act(async()=>calls[3].resolve([{...row,name:'Refreshed Professor',status:'ACTIVE'},...(mode==='create'?[{...row,id:2,name:'New Professor',professorId:'P2'}]:[])]));
  assert.equal(document.querySelector('form'),null);assert.match(host.textContent,/Refreshed Professor/);
  if(mode==='create')assert.match(host.textContent,/New Professor/);
  assert.ok(document.querySelector('[aria-label="Activate"]'));
  await click('[aria-label="Activate"]');
  assert.deepEqual(JSON.parse(calls[4].config.data),{status:'ACTIVE'});
  await act(async()=>calls[4].resolve({...row,status:'ACTIVE'}));
  assert.ok(document.querySelector('[aria-label="Deactivate"]'));assert.equal(calls.length,5);
 }finally{await act(async()=>root.unmount());host.remove();}
});
test('AD-12 coordinated reload applies only newest GET response',async()=>{
 const {useRemoteData}=await server.ssrLoadModule('/src/hooks/useRemoteData.js');
 const {getProfessors}=await server.ssrLoadModule('/src/services/admin/adminService.js');
 const calls=[];let reload;
 api.defaults.adapter=config=>new Promise(resolve=>calls.push({config,resolve:data=>resolve({config,data,status:200,headers:{}})}));
 function Harness(){const resource=useRemoteData(getProfessors,[]);reload=resource.reload;return React.createElement('p',{},resource.data.map(p=>p.name).join(','));}
 const host=document.createElement('div');document.body.append(host);const root=createRoot(host);
 try{
  await act(async()=>root.render(React.createElement(Harness)));
  await act(async()=>calls[0].resolve([row]));
  await act(async()=>{void reload();});
  await act(async()=>{void reload();});
  assert.equal(calls.length,3);assert.equal(calls[1].config.signal.aborted,true);
  await act(async()=>calls[2].resolve([{...row,name:'Newest'}]));
  await act(async()=>calls[1].resolve([{...row,name:'Stale'}]));
  assert.equal(host.textContent,'Newest');
 }finally{await act(async()=>root.unmount());host.remove();}
});

test('AD-13 formatters preserve valid labels and safely handle missing names', async () => {
 const {accountText,accountDisplay,accountInitial,accountTick}=await import('../src/utils/adminAccountDisplay.js');
 for(const value of [null,undefined,'','   ']){
  assert.equal(accountDisplay(value),'Unknown name');
  assert.equal(accountDisplay(value,'Email not provided'),'Email not provided');
  assert.equal(accountTick(value),'Unknown name');
  assert.equal(accountInitial(value),'?');
  assert.doesNotThrow(()=>accountText(value).toLowerCase());
 }
 assert.equal(accountDisplay('Dr. Example'),'Dr. Example');
 assert.equal(accountInitial('Dr. Example',true),'E');
 assert.equal(accountTick('Long Professor Name For Truncation'),'Long Professor Name Fo…');
});
for(const kind of ['Professor','Student'])test('AD-13 '+kind+' missing fields render, search and open details safely',async()=>{
 const Component=kind==='Professor'?Page:(await server.ssrLoadModule('/src/pages/admin/components/StudentManagement.jsx')).default;
 const records=[{...row,id:1,name:null,email:null},{...row,id:2,name:' ',email:''},{...row,id:3,name:'Valid Account',email:'valid@example.test'}];
 api.defaults.adapter=async config=>({config,data:records,status:200,headers:{}});
 const host=document.createElement('div');document.body.append(host);const root=createRoot(host);
 try {
  await act(async()=>root.render(React.createElement(MemoryRouter,{},React.createElement(AuthContext.Provider,{value:{role:'admin',user:{name:'Admin'}}},React.createElement(ToastContext.Provider,{value:{showToast(){}}},React.createElement(Component))))));
  assert.match(host.textContent,/Unknown name/);assert.match(host.textContent,/Email not provided/);
  assert.match(host.textContent,/Valid Account/);assert.match(host.textContent,/valid@example.test/);
  await act(async()=>document.querySelector('[aria-label="View"]').click());
  assert.match(host.textContent,/Unknown name/);
  await act(async()=>document.querySelector('[aria-label="Close"]').click());
  if(kind==='Professor'){
   await act(async()=>document.querySelector('[aria-label="Edit"]').click());
   assert.equal(document.querySelector('form input').value,'');
   await act(async()=>document.querySelector('[aria-label="Close"]').click());
  }
  const input=document.querySelector('input');
  await act(async()=>{Object.getOwnPropertyDescriptor(dom.window.HTMLInputElement.prototype,'value').set.call(input,'valid');input.dispatchEvent(new Event('input',{bubbles:true}));});
  assert.match(host.textContent,/Valid Account/);assert.doesNotMatch(host.textContent,/Unknown name/);
 }finally{await act(async()=>root.unmount());host.remove();}
});

for(const mode of ['create','edit','professor-details','student-details'])test('AD-16 '+mode+' labels, focus trap, Escape and opener restoration',async()=>{
 const Component=mode==='student-details'?(await server.ssrLoadModule('/src/pages/admin/components/StudentManagement.jsx')).default:Page;
 const host=document.createElement('div');document.body.append(host);const root=createRoot(host);
 api.defaults.adapter=async config=>({config,data:[row],status:200,headers:{}});
 try{
  await act(async()=>root.render(React.createElement(MemoryRouter,{},React.createElement(AuthContext.Provider,{value:{role:'admin',user:{name:'Admin'}}},React.createElement(ToastContext.Provider,{value:{showToast(){}}},React.createElement(Component))))));
  const opener=mode==='create'?[...host.querySelectorAll('button')].find(b=>b.textContent.includes('Create Professor')):host.querySelector(mode==='edit'?'[aria-label="Edit"]':'[aria-label="View"]');
  await act(async()=>{opener.focus();opener.click();});
  const dialog=document.querySelector('[role="dialog"]');assert.ok(dialog);assert.equal(dialog.getAttribute('aria-modal'),'true');
  assert.ok(document.getElementById(dialog.getAttribute('aria-labelledby')).textContent.trim());
  assert.ok(dialog.contains(document.activeElement));
  for(let i=0;i<12;i++)await act(async()=>{
   dialog.dispatchEvent(new dom.window.KeyboardEvent('keydown',{key:'Tab',shiftKey:i%2===0,bubbles:true}));
   assert.ok(dialog.contains(document.activeElement));
  });
  const controls=[...dialog.querySelectorAll('button,input')];
  await act(async()=>controls.at(-1).focus());
  assert.equal(document.activeElement,controls.at(-1));
  assert.ok(dialog.contains(document.activeElement));
  await act(async()=>dialog.dispatchEvent(new dom.window.KeyboardEvent('keydown',{key:'Escape',bubbles:true})));
  assert.equal(document.querySelector('[role="dialog"]'),null);assert.equal(document.activeElement,opener);
 }finally{await act(async()=>root.unmount());host.remove();}
});

test('AD-18 Admin modal styles bound the scroll area without clipping bottom actions', async () => {
 const {readFile}=await import('node:fs/promises');
 for(const path of ['src/pages/admin/styles/ProfessorManagement.module.css','src/pages/admin/styles/StudentManagement.module.css','src/components/shared/ChangePasswordModal.module.css']){
  const css=await readFile(new URL('../'+path,import.meta.url),'utf8');
  assert.match(css,/max-height:\s*calc\(100vh - 2rem\)/);
  assert.match(css,/max-height:\s*calc\(100dvh - 2rem\)/);
  assert.match(css,/overflow-y:\s*auto/);
  assert.match(css,/box-sizing:\s*border-box/);
  assert.match(css,/max-height:\s*500px/);
 }
});

test('AD-19 student list and details consistently label known and missing year levels',async()=>{
 const Component=(await server.ssrLoadModule('/src/pages/admin/components/StudentManagement.jsx')).default;
 for(const [yearLevel,label] of [['FIRST_YEAR','1st Year'],['SECOND_YEAR','2nd Year'],[null,'Not specified'],[undefined,'Not specified'],['','Not specified'],['   ','Not specified'],['THIRD_YEAR','Not specified']]){
  api.defaults.adapter=async config=>({config,data:[{...row,yearLevel}],status:200,headers:{}});
  const host=document.createElement('div');document.body.append(host);const root=createRoot(host);
  try{
   await act(async()=>root.render(React.createElement(MemoryRouter,{},React.createElement(AuthContext.Provider,{value:{role:'admin',user:{name:'Admin'}}},React.createElement(ToastContext.Provider,{value:{showToast(){}}},React.createElement(Component))))));
   assert.equal(host.querySelector('[aria-describedby="student-column-3"]').textContent,label);
   await act(async()=>host.querySelector('[aria-label="View"]').click());
   const dialog=document.querySelector('[role="dialog"]');
   const year=[...dialog.querySelectorAll('span')].find(el=>el.textContent==='Year Level');
   assert.equal(year.nextElementSibling.textContent,label);
  }finally{await act(async()=>root.unmount());host.remove();}
 }
});
