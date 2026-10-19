import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';
let dom,server,createRoot,Router,Register,ToastContext,api;
before(async()=>{
 dom=new JSDOM('<html><body></body></html>',{url:'http://localhost'});
 for(const key of ['window','document','HTMLElement','Event','MouseEvent','localStorage','sessionStorage'])globalThis[key]=dom.window[key];
 globalThis.requestAnimationFrame=callback=>setTimeout(callback,0);
 globalThis.IS_REACT_ACT_ENVIRONMENT=true;
 ({createRoot}=await import('react-dom/client'));Router=await import('react-router-dom');
 server=await createServer({server:{middlewareMode:true},appType:'custom'});
 Register=(await server.ssrLoadModule('/src/pages/login/components/Register.jsx')).default;
 ({ToastContext}=await server.ssrLoadModule('/src/context/notifications/useToast.js'));
 api=(await server.ssrLoadModule('/src/services/apiClient.js')).default;
});
after(async()=>{await server?.close();dom?.window.close();});
test('registration shows field errors, retains values and blocks duplicate submission',async()=>{
 const host=document.createElement('div');document.body.append(host);const root=createRoot(host);let calls=0,reject;
 api.defaults.adapter=config=>{calls++;const payload=JSON.parse(config.data);assert.equal(payload.studentId,'29AHRT-0168');assert.equal(payload.name,'Talidong, Archie V.');return new Promise((_,fail)=>{reject=fail;});};
 try{
  await act(async()=>root.render(React.createElement(Router.MemoryRouter,null,React.createElement(ToastContext.Provider,{value:{showToast:()=>{}}},React.createElement(Register)))));
  const submit=()=>host.querySelector('form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));
  await act(async()=>{submit();await new Promise(r=>setTimeout(r,1));});
  assert.equal(calls,0);assert.equal(host.querySelectorAll('[aria-invalid="true"]').length,7);
  for(const [key,value] of Object.entries({name:'Talidong, Archie V.',email:'archie@paterostechnologicalcollege.edu.ph',studentId:'29AHRT-0168',section:'BSHM 1A',yearLevel:'FIRST_YEAR',password:'password-123',confirmPassword:'password-123'})){
   await act(async()=>{const input=host.querySelector('#register-'+key);Object.getOwnPropertyDescriptor(key==='yearLevel'?window.HTMLSelectElement.prototype:window.HTMLInputElement.prototype,'value').set.call(input,value);input.dispatchEvent(new Event(key==='yearLevel'?'change':'input',{bubbles:true}));});
  }
  await act(async()=>{submit();submit();});assert.equal(calls,1);assert.equal(host.querySelector('[type="submit"]').disabled,true);
  await act(async()=>reject({response:{status:409,data:{code:'REGISTRATION_VALIDATION_FAILED',errors:{studentId:'This Student ID is already registered.'}}}}));
  assert.equal(host.querySelector('#register-studentId').value,'29AHRT-0168');assert.equal(host.querySelector('#register-studentId').getAttribute('aria-invalid'),'true');assert.match(host.textContent,/already registered/);assert.equal(host.querySelector('[type="submit"]').disabled,false);
 }finally{await act(async()=>root.unmount());host.remove();}
});
