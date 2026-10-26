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
  const data=config.url.endsWith('activity-logs')?{...base,content:config.params?.search?[]:config.params?.type==='AUTH'?[{id:2,userName:'Archie',action:'LOGIN_SUCCESS',displayAction:'Archie logged in',activityType:'AUTH',createdAt:'2026-01-01'}]:[{id:1,userName:'Actor',action:'Started Professor preview by Admin 20 viewing Professor 21',displayAction:'Talidong started Professor Preview for Juan Dela Cruz',activityType:'ACCOUNT',createdAt:'2026-01-01'}]}:
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
  assert.match(host.textContent,/Talidong started Professor Preview for Juan Dela Cruz/);assert.doesNotMatch(host.textContent,/by Admin 20|viewing Professor 21/);assert.equal(calls.at(-1).params.page,0);
  await click('Auth');assert.equal(calls.at(-1).params.type,'AUTH');assert.match(host.textContent,/Archie logged in/);assert.doesNotMatch(host.textContent,/LOGIN_SUCCESS/);
  await click('Next');assert.equal(calls.at(-1).params.page,1);
  const input=host.querySelector('input');
  await act(async()=>{Object.getOwnPropertyDescriptor(dom.window.HTMLInputElement.prototype,'value').set.call(input,'missing');input.dispatchEvent(new Event('input',{bubbles:true}));});
  assert.equal(calls.at(-1).params.search,'missing');assert.equal(calls.at(-1).params.page,0);
  assert.match(host.textContent,/No activity matches/);
  await click('Professor Activity');assert.equal(button('Professor Activity').getAttribute('aria-pressed'),'true');assert.equal(button('Activity Log').getAttribute('aria-pressed'),'false');assert.match(host.textContent,/50Active Professors/);assert.match(host.textContent,/100Total module activities/);assert.equal([...host.querySelectorAll('p')].filter(p=>/^Active professors$/i.test(p.textContent)).length,1);
  const signal=calls.at(-1).signal;await click('Next');assert.equal(calls.at(-1).params.page,1);assert.equal(signal.aborted,true);
  fail=true;await click('Previous');assert.match(host.textContent,/Couldn't load professor activity/);
  fail=false;await click('Retry');assert.match(host.textContent,/Active Professors/);
  await click('Student Performance');assert.match(host.textContent,/87%Average latest score/);assert.match(host.textContent,/90Passed assessment attempts/);
  assert.match(host.textContent,/No monitoring records/);
 }finally{await act(async()=>root.unmount());host.remove();}
 assert.equal(calls.at(-1).signal.aborted,true);
});

test('support list uses score threshold and positive empty state',async()=>{
 const host=document.createElement('div');document.body.append(host);const root=createRoot(host);
 let scores=[100,80,75,65,60,59,0,null];
 api.defaults.adapter=async config=>({config,status:200,headers:{},data:config.url.includes('students')?{
  number:0,totalPages:1,totalElements:scores.length,summary:{},content:scores.map((score,i)=>({userId:i,name:`Student ${score}`,latestAssessmentScore:score,profilePresent:score!==null})),
  highlights:scores.map((score,i)=>({userId:i,name:`Student ${score}`,latestAssessmentScore:score}))
 }:{number:0,totalPages:0,totalElements:0,content:[],summary:{},highlights:[]}});
 const click=async label=>act(async()=>[...host.querySelectorAll('button')].find(b=>b.textContent.trim()===label).click());
 try {
  await act(async()=>root.render(React.createElement(MemoryRouter,{},React.createElement(AuthContext.Provider,{value:{role:'admin',user:{name:'Admin'},logout(){}}},React.createElement(ToastContext.Provider,{value:{showToast(){}}},React.createElement(Monitoring))))));
  await click('Student Performance');
  assert.match(host.textContent,/Active Students/);
  assert.match(host.textContent,/Profile Incomplete/);
  assert.match(host.textContent,/YearNot set/);
  const panel=()=>[...host.querySelectorAll('h2')].find(h=>h.textContent==='Lowest-Scoring Students').parentElement;
  assert.match(panel().textContent,/Student 59/);assert.match(panel().textContent,/Student 0/);
  for(const score of [100,80,75,65,60,null])assert.ok(!panel().textContent.includes(`Student ${score}`));
  assert.equal(panel().querySelectorAll('svg').length,2);
  scores=[100,80,65,null];await click('Activity Log');await click('Student Performance');
  assert.match(panel().textContent,/No students currently need extra support\./);
  assert.equal(panel().querySelectorAll('svg').length,0);
 }finally{await act(async()=>root.unmount());host.remove();}
});

test('shared incomplete profile card renders safe academic details and hides when complete for both roles',async()=>{
 const Card=(await server.ssrLoadModule('/src/pages/admin/components/IncompleteProfiles.jsx')).default;
 const host=document.createElement('div');document.body.append(host);const root=createRoot(host);
 try {
  for(const role of ['student','professor']) {
   const complete={userId:1,name:'Complete',email:'complete@example.test',profileIncomplete:false,profilePresent:true};
   await act(async()=>root.render(React.createElement(Card,{role,accounts:[complete]})));
   assert.equal(host.textContent,'');
   const missing={userId:2,name:'Needs details',email:'details@example.test',profileIncomplete:true,profilePresent:false};
   await act(async()=>root.render(React.createElement(Card,{role,accounts:[complete,missing]})));
   assert.equal(host.querySelectorAll('li').length,1);
   assert.match(host.textContent,/Needs detailsdetails@example.test/);
   assert.match(host.textContent,/Incomplete/);assert.match(host.textContent,/IDNot set/);
   assert.doesNotMatch(host.textContent,/null|undefined|Complete/);
   if(role==='student') { assert.match(host.textContent,/YearNot set/);assert.match(host.textContent,/SectionNot set/); }
   await act(async()=>root.render(React.createElement(Card,{role,accounts:[]})));
   assert.equal(host.textContent,'');
  }
 }finally{await act(async()=>root.unmount());host.remove();}
});

test('average score distinguishes absent scores from genuine zero after loading and remount',async()=>{
 const host=document.createElement('div');document.body.append(host);const root=createRoot(host);
 let summary={},resolve;
 api.defaults.adapter=config=>config.url.includes('students')?new Promise(done=>{resolve=()=>done({config,status:200,headers:{},data:{number:0,totalPages:1,totalElements:15,content:[],highlights:[],summary}});}):Promise.resolve({config,status:200,headers:{},data:{number:0,totalPages:0,totalElements:0,content:[]}});
 const click=async label=>act(async()=>[...host.querySelectorAll('button')].find(b=>b.textContent.trim()===label).click());
 const render=()=>root.render(React.createElement(MemoryRouter,{},React.createElement(AuthContext.Provider,{value:{role:'admin',user:{name:'Admin'},logout(){}}},React.createElement(ToastContext.Provider,{value:{showToast(){}}},React.createElement(Monitoring)))));
 try {
  await act(async()=>render());
  for(const [next,expected] of [[{scored:0,averageScore:null},'No scored attempts'],[{},'No scored attempts'],[{scored:1,averageScore:0},'0%'],[{scored:3,averageScore:63.3333},'63%'],[{scored:0,averageScore:null},'No scored attempts']]) {
   summary={...next,passedAssessments:4};await click('Student Performance');
   assert.match(host.textContent,/Loading/);await act(async()=>resolve());
   const label=[...host.querySelectorAll('p')].find(p=>p.textContent==='Average latest score');
   assert.equal(label.previousElementSibling.textContent,expected);
   assert.match(host.textContent,/4Passed assessment attempts/);
   await click('Activity Log');
  }
 }finally{await act(async()=>root.unmount());host.remove();}
});
