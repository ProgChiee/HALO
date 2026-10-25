import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';
let dom,server,createRoot,Router,AuthContext;
before(async()=>{
 dom=new JSDOM('<html><body></body></html>',{url:'http://localhost'});
 for(const k of ['window','document','HTMLElement','Event','localStorage','sessionStorage'])globalThis[k]=dom.window[k];
 globalThis.IS_REACT_ACT_ENVIRONMENT=true;
 ({createRoot}=await import('react-dom/client'));Router=await import('react-router-dom');
 server=await createServer({server:{middlewareMode:true},appType:'custom'});
 ({AuthContext}=await server.ssrLoadModule('/src/context/login/useAuth.js'));


});
after(async()=>{await server.close();dom.window.close();});

test('Admin deletion confirms, sends authenticated DELETE, handles 204 and refreshes the row',async()=>{
 const Admins=(await server.ssrLoadModule('/src/pages/superadmin/components/Admins.jsx')).default;
 const {ToastContext}=await server.ssrLoadModule('/src/context/notifications/useToast.js');
 const api=(await server.ssrLoadModule('/src/services/apiClient.js')).default;
 window.matchMedia=()=>({matches:false,addEventListener(){},removeEventListener(){}});
 localStorage.setItem('halo_user',JSON.stringify({name:'Super Admin',email:'super@example.test',role:'SUPER_ADMIN'}));
 localStorage.setItem('halo_token','delete-test-token');
 let removed=false,requests=[];const notices=[];
 api.defaults.adapter=async config=>{requests.push(config);if(config.method==='delete')removed=true;return {data:config.method==='delete'?'':removed?[]:[{id:42,name:'Delete Target',email:'target@example.test',status:'ACTIVE'}],status:config.method==='delete'?204:200,headers:{},config};};
 const host=document.createElement('div');document.body.append(host);const root=createRoot(host);
 try {
  await act(async()=>root.render(React.createElement(AuthContext.Provider,{value:{user:{name:'Super Admin'},role:'superadmin'}},React.createElement(ToastContext.Provider,{value:{showToast:(...args)=>notices.push(args)}},React.createElement(Router.MemoryRouter,null,React.createElement(Admins))))));
  await act(async()=>host.querySelector('[aria-label="Delete Delete Target"]').click());
  assert.equal(removed,false);assert.match(document.querySelector('[role="dialog"]').textContent,/Delete Admin Account/);
  await act(async()=>[...document.querySelectorAll('[role="dialog"] button')].find(b=>b.textContent==='Cancel').click());assert.equal(removed,false);
  await act(async()=>host.querySelector('[aria-label="Delete Delete Target"]').click());
  await act(async()=>[...document.querySelectorAll('[role="dialog"] button')].find(b=>b.textContent==='Delete Account').click());
  assert.equal(host.querySelector('[aria-label="Delete Delete Target"]'),null);
  const request=requests.find(r=>r.method==='delete');assert.equal(request.url,'/super-admin/admin/42');assert.equal(request.headers.Authorization,'Bearer delete-test-token');
  assert.deepEqual(notices.at(-1),['Admin account deleted.','success']);
 }finally{await act(async()=>root.unmount());host.remove();localStorage.clear();}
});

test('protected-history response replaces deletion with real deactivation and keeps Cancel working', async()=>{
 const Admins=(await server.ssrLoadModule('/src/pages/superadmin/components/Admins.jsx')).default;
 const {ToastContext}=await server.ssrLoadModule('/src/context/notifications/useToast.js');
 const api=(await server.ssrLoadModule('/src/services/apiClient.js')).default;
 window.matchMedia=()=>({matches:false,addEventListener(){},removeEventListener(){}});
 let deletes=0,patches=0,status='ACTIVE';
 api.defaults.adapter=async config=>{
  if(config.method==='delete'){deletes++;throw {response:{status:409,data:{code:'ACCOUNT_HAS_PROTECTED_REFERENCES'}},config};}
  if(config.method==='patch'){patches++;assert.deepEqual(JSON.parse(config.data),{status:'INACTIVE'});status='INACTIVE';}
  return {data:config.method==='patch'?{status}:[{id:42,name:'Protected',email:'protected@example.test',status}],status:200,headers:{},config};
 };
 const host=document.createElement('div');document.body.append(host);const root=createRoot(host);
 const button=text=>[...document.querySelectorAll('[role="dialog"] button')].find(b=>b.textContent===text);
 try{
  await act(async()=>root.render(React.createElement(AuthContext.Provider,{value:{user:{name:'Super Admin'},role:'superadmin'}},React.createElement(ToastContext.Provider,{value:{showToast(){}}},React.createElement(Router.MemoryRouter,null,React.createElement(Admins))))));
  await act(async()=>host.querySelector('[aria-label="Delete Protected"]').click());
  const deleteButton=button('Delete Account');assert.equal(deleteButton.disabled,false);
  await act(async()=>{deleteButton.click();deleteButton.click();});
  assert.equal(deletes,1);assert.equal(button('Delete Account'),undefined);
  assert.match(document.querySelector('[role="dialog"]').textContent,/protected history and cannot be deleted/);
  await act(async()=>deleteButton.click());assert.equal(deletes,1);
  assert.ok(button('Deactivate Account'));
  await act(async()=>button('Cancel').click());assert.equal(document.querySelector('[role="dialog"]'),null);
  await act(async()=>host.querySelector('[aria-label="Delete Protected"]').click());
  await act(async()=>button('Delete Account').click());
  await act(async()=>{button('Deactivate Account').click();});
  assert.equal(patches,0);
  assert.match(document.querySelector('[role="dialog"]').textContent,/Deactivate Admin Account/);
  await act(async()=>button('Deactivate').click());
  assert.equal(patches,1);assert.equal(deletes,2);assert.equal(document.querySelector('[role="dialog"]'),null);
  assert.ok(host.querySelector('[aria-label="Activate"]'));
  await act(async()=>host.querySelector('[aria-label="Delete Protected"]').click());
  await act(async()=>button('Delete Account').click());
  assert.equal(button('Delete Account'),undefined);assert.equal(button('Deactivate Account'),undefined);
  await act(async()=>button('Cancel').click());assert.equal(document.querySelector('[role="dialog"]'),null);
 }finally{await act(async()=>root.unmount());host.remove();}
});
