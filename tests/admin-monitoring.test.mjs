import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';
let server, dom, createRoot, Monitoring, AuthContext, ToastContext, MemoryRouter, api;
before(async () => {
 dom=new JSDOM('<html><body></body></html>', {url:'http://localhost'});
 for(const key of ['window','document','HTMLElement','Event','MouseEvent','localStorage','sessionStorage']) globalThis[key]=dom.window[key];
 globalThis.IS_REACT_ACT_ENVIRONMENT=true;
 globalThis.ResizeObserver=class { observe() {} unobserve() {} disconnect() {} };
 ({createRoot}=await import('react-dom/client'));({MemoryRouter}=await import('react-router-dom'));
 server=await createServer({server:{middlewareMode:true},appType:'custom'});
 Monitoring=(await server.ssrLoadModule('/src/pages/admin/components/Monitoring.jsx')).default;
 ({AuthContext}=await server.ssrLoadModule('/src/context/login/useAuth.js'));
 ({ToastContext}=await server.ssrLoadModule('/src/context/notifications/useToast.js'));
 api=(await server.ssrLoadModule('/src/services/apiClient.js')).default;
});
after(async()=>{await server.close();dom.window.close();});
test('Admin monitoring pages preserve global metrics, server filters, retries, empty states and cancellation',async()=>{
 const host=document.createElement('div');document.body.append(host);const root=createRoot(host);const calls=[];
 let fail=false;
 api.defaults.adapter=async config=>{
  calls.push(config);
  if(fail)throw {response:{status:500,data:{code:'ADMIN_REQUEST_FAILED'}}};
  const base={number:config.params?.page??0,size:25,totalElements:50,totalPages:2};
  const data=config.url.endsWith('activity-logs')?{...base,content:config.params?.search?[]:[{id:1,userName:'Actor',action:'Created account',activityType:'ACCOUNT',createdAt:'2026-01-01'}]}:
   config.url.includes('professors')?{...base,content:[{userId:1,name:'Professor',moduleActivities:2}],summary:{active:45,moduleActivities:100},highlights:[]}:
   {...base,content:[],summary:{scored:40,averageScore:87,passedAssessments:90},highlights:[]};
  return {config,status:200,headers:{},data};
 };
 const button=label=>[...host.querySelectorAll('button')].find(b=>b.textContent.trim()===label);
 const click=async label=>act(async()=>button(label).click());
 try {
  await act(async()=>root.render(React.createElement(MemoryRouter,{},React.createElement(AuthContext.Provider,{value:{role:'admin',user:{name:'Admin'},logout(){}}},React.createElement(ToastContext.Provider,{value:{showToast(){}}},React.createElement(Monitoring))))));
  assert.equal(button('Activity Log').getAttribute('aria-pressed'),'true');
  assert.equal(host.querySelector('input').getAttribute('aria-label'),'Search activity log');
  assert.ok(host.querySelectorAll('button[aria-pressed="true"]').length >= 2);
  assert.match(host.textContent,/Created account/);assert.equal(calls.at(-1).params.page,0);
  await click('Next');assert.equal(calls.at(-1).params.page,1);
  const input=host.querySelector('input');
  await act(async()=>{Object.getOwnPropertyDescriptor(dom.window.HTMLInputElement.prototype,'value').set.call(input,'missing');input.dispatchEvent(new Event('input',{bubbles:true}));});
  assert.equal(calls.at(-1).params.search,'missing');assert.equal(calls.at(-1).params.page,0);
  assert.match(host.textContent,/No activity matches/);
  await click('Professor Activity');assert.equal(button('Professor Activity').getAttribute('aria-pressed'),'true');assert.equal(button('Activity Log').getAttribute('aria-pressed'),'false');assert.match(host.textContent,/50Total professors/);assert.match(host.textContent,/100Total module activities/);
  const signal=calls.at(-1).signal;await click('Next');assert.equal(calls.at(-1).params.page,1);assert.equal(signal.aborted,true);
  fail=true;await click('Previous');assert.match(host.textContent,/Couldn't load professor activity/);
  fail=false;await click('Retry');assert.match(host.textContent,/Total professors/);
  await click('Student Performance');assert.match(host.textContent,/87%Average latest score/);assert.match(host.textContent,/90Total passed assessments/);
  assert.match(host.textContent,/No monitoring records/);
 }finally{await act(async()=>root.unmount());host.remove();}
 assert.equal(calls.at(-1).signal.aborted,true);
});
