import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';
let dom,server,createRoot,Router,AuthProvider,useAuth,Editor,Sidebar,api,writeSession,readSession;
before(async()=>{
 dom=new JSDOM('<html><body></body></html>',{url:'http://localhost'});
 for(const key of ['window','document','HTMLElement','Event','MouseEvent','localStorage','sessionStorage'])globalThis[key]=dom.window[key];
 globalThis.IS_REACT_ACT_ENVIRONMENT=true;
 ({createRoot}=await import('react-dom/client'));Router=await import('react-router-dom');
 server=await createServer({server:{middlewareMode:true},appType:'custom'});
 ({AuthProvider}=await server.ssrLoadModule('/src/context/login/AuthContext.jsx'));
 ({useAuth}=await server.ssrLoadModule('/src/context/login/useAuth.js'));
 Editor=(await server.ssrLoadModule('/src/components/shared/ProfileName.jsx')).default;
 Sidebar=(await server.ssrLoadModule('/src/components/shared/Sidebar.jsx')).default;
 api=(await server.ssrLoadModule('/src/services/apiClient.js')).default;
 ({writeSession,readSession}=await server.ssrLoadModule('/src/utils/session.js'));
});
after(async()=>{await server.close();dom.window.close();});
function Content({readOnly=false}){const {user}=useAuth();return React.createElement(React.Fragment,null,React.createElement(Sidebar),React.createElement('output',null,user.name),React.createElement(Editor,{name:readOnly?'Target Professor':user.name,readOnly}));}
async function change(host,value){await act(async()=>{const input=host.querySelector('input');Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value').set.call(input,value);input.dispatchEvent(new Event('input',{bubbles:true}));});}
async function click(host,label){await act(async()=>[...host.querySelectorAll('button')].find(b=>b.textContent===label || b.getAttribute('aria-label')===label).click());}
test('all roles edit name, synchronize sidebar and storage, retain errors and prevent duplicate requests',async()=>{
 for(const role of ['student','professor','admin','superadmin']){
  writeSession({name:'Original',email:role+'@example.test',role},'token-'+role,false);
  let calls=0,fail=false,finish;const photoCalls=[];
  api.defaults.adapter=async config=>{
   if(config.method==='patch'){calls++;assert.equal(config.url,'/profile');assert.equal(config.headers.Authorization,'Bearer token-'+role);if(fail)throw Object.assign(new Error('SQL secret'),{response:{status:500,data:{code:'INTERNAL_SERVER_ERROR'}}});return new Promise(resolve=>{finish=()=>resolve({config,status:200,headers:{},data:{userId:1,name:JSON.parse(config.data).fullName}});});}
   photoCalls.push(config.url);return {config,status:204,headers:{},data:null};
  };
  const host=document.createElement('div');document.body.append(host);const root=createRoot(host);
  try{
   await act(async()=>root.render(React.createElement(AuthProvider,null,React.createElement(Router.MemoryRouter,null,React.createElement(Content)))));
   await click(host,'Edit full name');await change(host,'   ');await click(host,'Save Changes');assert.match(host.textContent,/Full name is required/);assert.equal(calls,0);
   await change(host,"  Talidong,   Archie V.  ");
   await act(async()=>{host.querySelector('form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));host.querySelector('form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));});
   assert.equal(calls,1);assert.match(host.textContent,/Saving/);
   await act(async()=>finish());assert.match(host.querySelector('aside').textContent,/Talidong, Archie V./);assert.equal(host.querySelector('output').textContent,"Talidong, Archie V.");assert.equal(readSession().user.name,"Talidong, Archie V.");assert.equal(readSession().user.email,role+'@example.test');assert.equal(localStorage.getItem('halo_token'),null);assert.equal(photoCalls.length,1);
   await click(host,'Edit full name');await change(host,'Keep draft');fail=true;await click(host,'Save Changes');assert.equal(host.querySelector('input').value,'Keep draft');assert.match(host.textContent,/server could not save/);assert.doesNotMatch(host.textContent,/SQL secret/);await click(host,'Cancel');assert.equal(host.querySelector('input'),null);
  }finally{await act(async()=>root.unmount());host.remove();}
 }
});
test('acting profile is read-only and retains Admin identity',async()=>{
 writeSession({name:'Admin Actor',email:'admin@example.test',role:'admin'},'actor-token');
 let patches=0;api.defaults.adapter=async config=>{if(config.method==='patch')patches++;return {config,status:204,headers:{},data:null};};
 const host=document.createElement('div');document.body.append(host);const root=createRoot(host);
 try{await act(async()=>root.render(React.createElement(AuthProvider,null,React.createElement(Router.MemoryRouter,null,React.createElement(Content,{readOnly:true})))));
 assert.match(host.textContent,/Target Professor/);assert.match(host.querySelector('aside').textContent,/Admin Actor/);assert.equal(host.querySelector('[aria-label="Edit full name"]'),null);assert.equal(patches,0);
 }finally{await act(async()=>root.unmount());host.remove();}
});
