import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';
let dom, server, createRoot, Router, AuthContext, ToastContext, api;
before(async () => {
  dom = new JSDOM('<html><body></body></html>', { url: 'http://localhost' });
  for (const key of ['window', 'document', 'HTMLElement', 'Event', 'localStorage', 'sessionStorage']) globalThis[key] = dom.window[key];
  globalThis.IS_REACT_ACT_ENVIRONMENT = true;
  ({ createRoot } = await import('react-dom/client'));
  Router = await import('react-router-dom');
  server = await createServer({ server: { middlewareMode: true }, appType: 'custom' });
  ({ AuthContext } = await server.ssrLoadModule('/src/context/login/useAuth.js'));
  ({ ToastContext } = await server.ssrLoadModule('/src/context/notifications/useToast.js'));
  api = (await server.ssrLoadModule('/src/services/apiClient.js')).default;
});
after(async () => { await server?.close(); dom?.window.close(); });


async function mount(name = 'StudentProgress') {
 const Component=(await server.ssrLoadModule(`/src/pages/professor/components/${name}.jsx`)).default;
 const host=document.createElement('div');document.body.append(host);const root=createRoot(host);
 await act(async()=>root.render(React.createElement(AuthContext.Provider,{value:{user:{name:'Professor'},logout(){}}},React.createElement(Router.MemoryRouter,{},React.createElement(Component)))));
 return {host, async close(){await act(async()=>root.unmount());host.remove();}};
}
const row=(userId,name)=>({userId,name,section:null,completedModules:0,passedAssessments:0,totalBadges:1});
const page=(number,content)=>({content,number,size:20,totalElements:21,totalPages:2,first:number===0,last:number===1,badgeScope:'INSTITUTION_WIDE'});
const button=(host,label)=>[...host.querySelectorAll('button')].filter(b=>!b.closest('[aria-labelledby="learning-engagement-title"]')).find(b=>b.textContent===label);

test('one request per page, clears previous rows while loading, renders zero counts',async()=>{
 const adapter=api.defaults.adapter,calls=[];let resolveNext;
 api.defaults.adapter=config=>{
  if(config.url.endsWith('/engagement'))return Promise.resolve({status:200,headers:{},config,data:{content:[],first:true,last:true,totalPages:0}});
  calls.push({url:config.url,params:config.params});
  const response=data=>({status:200,headers:{},config,data});
  return config.params.page===0?Promise.resolve(response(page(0,[row(1,'Student A')]))):new Promise(resolve=>{resolveNext=()=>resolve(response(page(1,[row(2,'Student B')])));});
 };
 const ui=await mount();
 try {
  assert.match(ui.host.textContent,/Student A/);assert.match(ui.host.textContent,/Institution-wide Achievements/);
  assert.ok(ui.host.textContent.includes('Modules completed (this page)'));assert.ok(!ui.host.textContent.includes('Unavailable'));
  assert.equal(calls.length,1);assert.equal(button(ui.host,'Previous').disabled,true);
  await act(async()=>button(ui.host,'Next').click());
  assert.match(ui.host.textContent,/Loading student progress/);assert.ok(!ui.host.textContent.includes('Student A'));
  await act(async()=>resolveNext());assert.match(ui.host.textContent,/Student B/);assert.equal(button(ui.host,'Next').disabled,true);
  assert.deepEqual(calls,[{url:'/professor/students/progress-summaries',params:{page:0,size:20}},{url:'/professor/students/progress-summaries',params:{page:1,size:20}}]);
  await act(async()=>button(ui.host,'Previous').click());assert.match(ui.host.textContent,/Student A/);assert.equal(calls.length,3);
 }finally{await ui.close();api.defaults.adapter=adapter;}
});
test('summary failure has retry and empty page renders without detail requests',async()=>{
 const adapter=api.defaults.adapter,calls=[];let fail=true;
 api.defaults.adapter=async config=>{
  if(config.url.endsWith('/engagement'))return {status:200,headers:{},config,data:{content:[],first:true,last:true,totalPages:0}};
  calls.push(config.url);if(fail)throw new Error('unavailable');
  return {status:200,headers:{},config,data:{...page(0,[]),totalElements:0,totalPages:0,last:true}};
 };
 const ui=await mount();
 try {
  assert.match(ui.host.textContent,/Couldn't load student progress/);assert.ok(button(ui.host,'Retry'));
  fail=false;await act(async()=>button(ui.host,'Retry').click());
  assert.match(ui.host.textContent,/No student accounts found/);assert.equal(button(ui.host,'Next').disabled,true);
  assert.deepEqual(calls,Array(2).fill('/professor/students/progress-summaries'));
 }finally{await ui.close();api.defaults.adapter=adapter;}
});

test('Learning Engagement renders selected scoped students with zero metrics and independent retry',async()=>{
 let fail=true;const calls=[];
 api.defaults.adapter=async config=>{calls.push(config);if(fail)throw {response:{status:503}};return {status:200,headers:{},config,data:{...page(0,[{userId:8,name:'In-scope Student',activeDaysWeek:0,activeDaysMonth:3,mentorSessionsWeek:0,mentorSessionsMonth:1}]),last:true,timezone:'Asia/Manila'}};};
 const ui=await mount('LearningEngagement');try{
  assert.match(ui.host.textContent,/Please try again later/);fail=false;
  await act(async()=>[...ui.host.querySelectorAll('button')].find(b=>b.textContent==='Retry engagement').click());
  const cells=[...ui.host.querySelectorAll('tbody td')].map(c=>c.textContent);assert.deepEqual(cells,['0','3','0','1']);assert.match(ui.host.textContent,/In-scope Student/);assert.match(ui.host.textContent,/Asia\/Manila/);
  assert.equal(calls.length,2);assert.ok(calls.every(c=>c.url==='/professor/students/engagement'&&c.signal));
 }finally{await ui.close();}
});
