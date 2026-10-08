import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';
let server, dom, createRoot, Dashboard, AuthContext, MemoryRouter, api;
before(async () => {
 dom=new JSDOM('<html><body></body></html>', {url:'http://localhost'});
 for(const key of ['window','document','HTMLElement','Event','localStorage','sessionStorage'])globalThis[key]=dom.window[key];
 globalThis.IS_REACT_ACT_ENVIRONMENT=true;
 ({createRoot}=await import('react-dom/client'));({MemoryRouter}=await import('react-router-dom'));
 server=await createServer({server:{middlewareMode:true},appType:'custom'});
 Dashboard=(await server.ssrLoadModule('/src/pages/admin/components/AdminDashboard.jsx')).default;
 ({AuthContext}=await server.ssrLoadModule('/src/context/login/useAuth.js'));
 api=(await server.ssrLoadModule('/src/services/apiClient.js')).default;
});
after(async()=>{await server.close();dom.window.close();});
const stats={totalStudents:12,totalProfessors:3,activeUsers:15,inactiveUsers:1,totalSubjects:4,totalModules:8};
const activity=[{id:1,userName:'Actor',action:'Saved a subject',createdAt:'2026-01-01'}];
for(const statsOK of [true,false])for(const activityOK of [true,false])test(`independent sections stats=${statsOK}, activity=${activityOK} and targeted retries`,async()=>{
 const host=document.createElement('div');document.body.append(host);const root=createRoot(host);const calls=[];
 api.defaults.adapter=config=>new Promise((resolve,reject)=>calls.push({config,resolve:data=>resolve({config,data,status:200,headers:{}}),reject}));
 try {
  await act(async()=>root.render(React.createElement(MemoryRouter,{},React.createElement(AuthContext.Provider,{value:{role:'admin',user:{name:'Admin'},logout(){}}},React.createElement(Dashboard)))));
  assert.equal(calls.length,2);
  const statsCall=calls.find(c=>!c.config.url.endsWith('recent-activity'));const activityCall=calls.find(c=>c.config.url.endsWith('recent-activity'));
  const settle=(call,ok,data)=>ok?call.resolve(data):call.reject({response:{status:500,data:{code:'ADMIN_REQUEST_FAILED'}}});
  await act(async()=>settle(statsCall,statsOK,stats));
  assert.equal(host.textContent.includes('Total Students'),statsOK);
  assert.match(host.textContent,/Loading recent activity/);
  await act(async()=>settle(activityCall,activityOK,activity));
  assert.equal(host.textContent.includes('Total Students'),statsOK);
  assert.equal(host.textContent.includes('Saved a subject'),activityOK);
  for(const [ok,label,url,data] of [[statsOK,'Retry statistics',statsCall.config.url,stats],[activityOK,'Retry activity',activityCall.config.url,activity]]){
   if(ok)continue;
   const previous=calls.length;
   await act(async()=>[...host.querySelectorAll('button')].find(b=>b.textContent===label).click());
   assert.equal(calls.length,previous+1);assert.equal(calls.at(-1).config.url,url);
   await act(async()=>calls.at(-1).resolve(data));
  }
  assert.match(host.textContent,/Total Students/);assert.match(host.textContent,/Saved a subject/);
 }finally{await act(async()=>root.unmount());host.remove();}
 assert.ok(calls.every(c=>c.config.signal.aborted));
});
test('unmount aborts both pending requests and ignores late responses',async()=>{
 const host=document.createElement('div');document.body.append(host);const root=createRoot(host);const calls=[];
 api.defaults.adapter=config=>new Promise(resolve=>calls.push({config,resolve}));
 await act(async()=>root.render(React.createElement(MemoryRouter,{},React.createElement(AuthContext.Provider,{value:{role:'admin',user:{name:'Admin'},logout(){}}},React.createElement(Dashboard)))));
 await act(async()=>root.unmount());assert.equal(calls.length,2);assert.ok(calls.every(c=>c.config.signal.aborted));
 await act(async()=>calls.forEach(c=>c.resolve({config:c.config,data:c.config.url.endsWith('recent-activity')?activity:stats,status:200,headers:{}})));
 assert.equal(host.textContent,'');host.remove();
});
