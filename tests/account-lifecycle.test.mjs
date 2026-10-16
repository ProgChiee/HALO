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


for(const kind of ['Student','Professor']) test(kind+' lifecycle filters, archive and restore retain activation state',async()=>{
 const Page=(await server.ssrLoadModule('/src/pages/admin/components/'+kind+'Management.jsx')).default;
 const {ToastContext}=await server.ssrLoadModule('/src/context/notifications/useToast.js');
 const api=(await server.ssrLoadModule('/src/services/apiClient.js')).default;
 window.matchMedia=()=>({matches:false,addEventListener(){},removeEventListener(){}});
 let record={id:1,name:'Lifecycle Account',email:'lifecycle@example.test',professorId:'P1',status:'ACTIVE'},previous;const requests=[];
 api.defaults.adapter=async config=>{requests.push(config);
  if(config.url.endsWith('/archive')){previous=record.status;record={...record,status:'ARCHIVED'};}
  else if(config.url.endsWith('/restore'))record={...record,status:previous};
  else if(config.method==='patch')record={...record,status:JSON.parse(config.data).status};
  return {data:config.method==='get'?[record]:record,status:200,headers:{},config};};
 const host=document.createElement('div');document.body.append(host);const root=createRoot(host);
 const tab=async text=>act(async()=>[...host.querySelectorAll('[aria-label="Account state"] button')].find(b=>b.textContent===text).click());
 const action=async text=>{await act(async()=>host.querySelector('[aria-label="'+text+'"]').click());await act(async()=>[...document.querySelectorAll('[role="dialog"] button')].find(b=>b.textContent===(text==='Archive'?'Archive '+kind:text)).click());};
 try{
  await act(async()=>root.render(React.createElement(AuthContext.Provider,{value:{role:'admin',user:{name:'Admin'}}},React.createElement(ToastContext.Provider,{value:{showToast(){}}},React.createElement(Router.MemoryRouter,null,React.createElement(Page))))));
  await action('Archive');assert.equal(record.status,'ARCHIVED');assert.equal(host.querySelector('[aria-label="Archive"]'),null);
  await tab('Archived');
  const search=host.querySelector('input[aria-label^="Search"]');
  const query=async value=>act(async()=>{Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value').set.call(search,value);search.dispatchEvent(new Event('input',{bubbles:true}));});
  await query('not a matching account');assert.equal(host.querySelector('[aria-label="View"]'),null);
  await query('lifecycle');assert.ok(host.querySelector('[aria-label="View"]'));
  await query('');assert.ok(host.querySelector('[aria-label="View"]'));assert.equal(host.querySelector('[aria-label="Edit"]'),null);
  assert.equal(host.querySelector('[aria-label="Reactivate"]'),null);await action('Restore');assert.equal(record.status,'ACTIVE');
  await tab('Active');await action('Deactivate');assert.equal(record.status,'INACTIVE');
  await tab('Inactive');assert.equal(host.querySelector('[aria-label="Deactivate"]'),null);await action('Archive');
  await tab('Archived');await action('Restore');assert.equal(record.status,'INACTIVE');
  await tab('Inactive');await action('Reactivate');assert.equal(record.status,'ACTIVE');
  assert.equal(requests.filter(r=>r.method==='delete').length,0);
 }finally{await act(async()=>root.unmount());host.remove();}
});
