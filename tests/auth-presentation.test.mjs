import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';
let dom,server,createRoot,Router,Login,Register,AuthContext,ToastContext,api;
before(async()=>{
 dom=new JSDOM('<html><body></body></html>',{url:'http://localhost'});
 for(const key of ['window','document','HTMLElement','Event','localStorage','sessionStorage'])globalThis[key]=dom.window[key];
 globalThis.IS_REACT_ACT_ENVIRONMENT=true;
 ({createRoot}=await import('react-dom/client'));Router=await import('react-router-dom');
 server=await createServer({server:{middlewareMode:true},appType:'custom'});
 Login=(await server.ssrLoadModule('/src/pages/login/components/Login.jsx')).default;
 Register=(await server.ssrLoadModule('/src/pages/login/components/Register.jsx')).default;
 ({AuthContext}=await server.ssrLoadModule('/src/context/login/useAuth.js'));
 ({ToastContext}=await server.ssrLoadModule('/src/context/notifications/useToast.js'));
 api=(await server.ssrLoadModule('/src/services/apiClient.js')).default;
});
after(async()=>{await server?.close();dom?.window.close();});
async function mount(Component,login=()=>{}){
 const host=document.createElement('div');document.body.append(host);const root=createRoot(host);
 function Location(){return React.createElement('output',null,Router.useLocation().pathname);}
 await act(async()=>root.render(React.createElement(Router.MemoryRouter,{initialEntries:['/login']},React.createElement(AuthContext.Provider,{value:{login}},React.createElement(ToastContext.Provider,{value:{showToast(){}}},React.createElement(Component),React.createElement(Location))))));
 return {host,async close(){await act(async()=>root.unmount());host.remove();}};
}
async function fill(input,value){await act(async()=>{Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value').set.call(input,value);input.dispatchEvent(new Event('input',{bubbles:true}));});}
test('Login has visible labels, functional visibility, clear pending/error states and unchanged session payload',async()=>{
 const adapter=api.defaults.adapter,calls=[],sessions=[];let reject,resolve;
 api.defaults.adapter=config=>{calls.push(config);return new Promise((yes,no)=>{resolve=data=>yes({config,status:200,headers:{},data});reject=no;});};
 const view=await mount(Login,(...args)=>sessions.push(args));
 try {
  const {host}=view;
  assert.equal(host.querySelector('h1').textContent,'Welcome back');assert.equal(host.querySelector('img').alt,'HALO logo');
  for(const input of host.querySelectorAll('input:not([type=checkbox])'))assert.ok(input.labels?.length);
  assert.equal(host.querySelectorAll('svg').length,1);
  assert.equal(host.querySelector('a[href="/forgot-password"]').textContent.trim(),'Forgot password?');assert.ok(host.querySelector('a[href="/register"]'));
  await fill(host.querySelector('#login-email'),'student@example.test');await fill(host.querySelector('#login-password'),'password-123');
  const toggle=host.querySelector('[aria-controls="login-password"]');await act(async()=>toggle.click());
  assert.equal(host.querySelector('#login-password').type,'text');assert.equal(toggle.getAttribute('aria-pressed'),'true');
  await act(async()=>toggle.click());assert.equal(host.querySelector('#login-password').type,'password');
  await act(async()=>host.querySelector('input[type=checkbox]').click());
  const submit=()=>host.querySelector('form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));
  await act(async()=>submit());assert.equal(calls.length,1);assert.deepEqual(JSON.parse(calls[0].data),{email:'student@example.test',password:'password-123'});
  assert.equal(host.querySelector('[type=submit]').disabled,true);assert.equal(toggle.disabled,true);
  await act(async()=>reject({response:{status:401}}));assert.match(host.querySelector('[role=alert]').textContent,/Login failed/);assert.equal(host.querySelector('#login-email').value,'student@example.test');
  await act(async()=>submit());await act(async()=>resolve({name:'Student',email:'student@example.test',role:'STUDENT',token:'synthetic-test-token'}));
  assert.equal(sessions[0][2],false);assert.equal(host.querySelector('output').textContent,'/student');
 }finally{await view.close();api.defaults.adapter=adapter;}
});
test('Sign Up retains every labeled field, year options, hints and independent password toggles without decorative icons',async()=>{
 const view=await mount(Register);try{
  const {host}=view;assert.equal(host.querySelector('h1').textContent,'Create your account');assert.equal(host.querySelectorAll('img').length,0);
  assert.equal(host.querySelectorAll('input,select').length,7);
  for(const input of host.querySelectorAll('input,select')){assert.ok(input.labels?.length);assert.ok(document.getElementById(input.getAttribute('aria-describedby')));}
  assert.deepEqual([...host.querySelectorAll('option')].map(o=>o.value),['','FIRST_YEAR','SECOND_YEAR']);
  assert.equal(host.querySelector('#register-studentId').placeholder,'e.g. 29AHRT-0168');
  for(const field of ['password','confirmPassword']){const toggle=host.querySelector(`[aria-controls="register-${field}"]`);await act(async()=>toggle.click());assert.equal(host.querySelector('#register-'+field).type,'text');}
  assert.equal(host.querySelectorAll('svg').length,3);assert.equal(host.querySelector('[type=submit]').textContent,'Create Account');assert.ok(host.querySelector('a[href="/login"]'));
 }finally{await view.close();}
});
test('auth layouts share tokens, retained background, fluid card sizing and mobile-first column rules',async()=>{
 const css=await readFile(new URL('../src/pages/login/styles/AuthForm.module.css',import.meta.url),'utf8');
 const loginCss=await readFile(new URL('../src/pages/login/styles/Login.module.css',import.meta.url),'utf8');
 const registerCss=await readFile(new URL('../src/pages/login/styles/Register.module.css',import.meta.url),'utf8');
 assert.match(css,/composes: background from/);assert.match(css,/margin-block: auto/);assert.match(css,/min-height: 48px/);assert.match(css,/focus-visible/);assert.match(css,/aria-invalid/);
 assert.match(loginCss,/max-width: 440px/);assert.match(loginCss,/width: 184px/);assert.match(registerCss,/max-width: 680px/);
 assert.match(registerCss,/grid-template-columns: minmax\(0, 1fr\)/);assert.match(registerCss,/@media \(min-width: 601px\)/);assert.match(registerCss,/repeat\(2, minmax\(0, 1fr\)\)/);assert.match(css,/@media \(max-width: 480px\)/);
 for(const Component of [Login,Register]){const view=await mount(Component);try{assert.match(view.host.querySelector('main').style.getPropertyValue('--bg-image'),/Background\.png/);}finally{await view.close();}}
});
