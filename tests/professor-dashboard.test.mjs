import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';
let dom,server,createRoot,Router,AuthContext,ToastContext,Workflow,Dashboard;
before(async()=>{
 dom=new JSDOM('<html><body></body></html>',{url:'http://localhost'});
 for(const key of ['window','document','HTMLElement','Event','localStorage','sessionStorage'])globalThis[key]=dom.window[key];
 globalThis.IS_REACT_ACT_ENVIRONMENT=true;
 globalThis.ResizeObserver=class{observe(){}unobserve(){}disconnect(){}};
 window.matchMedia=()=>({matches:false,addEventListener(){},removeEventListener(){}});
 ({createRoot}=await import('react-dom/client'));Router=await import('react-router-dom');
 server=await createServer({server:{middlewareMode:true},appType:'custom'});
 ({AuthContext}=await server.ssrLoadModule('/src/context/login/useAuth.js'));
 ({ToastContext}=await server.ssrLoadModule('/src/context/notifications/useToast.js'));
 ({ProfessorWorkflowContext:Workflow}=await server.ssrLoadModule('/src/context/professor/useProfessorWorkflow.js'));
 Dashboard=(await server.ssrLoadModule('/src/pages/professor/components/ProfessorDashboard.jsx')).default;
});
after(async()=>{await server.close();dom.window.close();});
async function mount(load,basePath='/professor',preview=false){
 const host=document.createElement('div');document.body.append(host);const root=createRoot(host);
 await act(async()=>root.render(React.createElement(AuthContext.Provider,{value:{user:{name:'Professor'},logout(){}}},React.createElement(ToastContext.Provider,{value:{showToast(){}}},React.createElement(Workflow.Provider,{value:{api:{getProfessorDashboardData:load},basePath,preview}},React.createElement(Router.MemoryRouter,{},React.createElement(Dashboard)))))));
 return {host,close:async()=>{await act(async()=>root.unmount());host.remove();}};
}
const data={totalStudents:3,totalSubjects:2,totalModules:5,approvedModules:4,totalAssessments:4,totalPassedAttempts:8,performance:[{day:'2026-10-18',averageScore:82.5,attempts:2}],attention:[{kind:'assessment',count:1,lessons:[{moduleId:37,subjectId:2,weekId:3,title:'Guest care'}]},{kind:'revision',count:0,lessons:[]},{kind:'approval',count:0,lessons:[]}],recentActivity:[{id:1,description:'Professor approved quiz version 4',createdAt:'2026-10-18T10:00:00'}]};
test('teaching overview renders real response values, accessible performance data and scoped lesson links',async()=>{
 const ui=await mount(async()=>data);try{
  assert.match(ui.host.textContent,/Teaching Overview/);assert.doesNotMatch(ui.host.textContent,/Institution-wide/);
  const cards=ui.host.querySelectorAll('[aria-label="Teaching summary"] a');assert.equal(cards.length,6);assert.match(cards[0].textContent,/3My Students/);
  assert.match(ui.host.querySelector('table').textContent,/82.5%/);assert.match(ui.host.textContent,/Professor approved quiz version 4/);
  assert.equal([...ui.host.querySelectorAll('a')].find(a=>a.textContent.includes('Guest care')).getAttribute('href'),'/professor/subjects/2/week/3');
 }finally{await ui.close();}
});
test('empty/error states are honest and Preview links stay in its read-only context',async()=>{
 const ui=await mount(async()=>({...data,performance:[],recentActivity:[],attention:[]}),'/admin/professor-preview/session',true);try{assert.match(ui.host.textContent,/No assessment results yet/);assert.equal(ui.host.querySelector('table'),null);assert.match(ui.host.textContent,/No pending teaching tasks/);assert.equal(ui.host.querySelector('[aria-label="Teaching summary"] a').getAttribute('href'),'/admin/professor-preview/session/progress');}finally{await ui.close();}
 let calls=0;const failed=await mount(async()=>{if(!calls++)throw new Error('Unavailable');return{...data,performance:[]};});try{assert.ok(failed.host.querySelector('[role="alert"]'));await act(async()=>[...failed.host.querySelectorAll('button')].find(b=>b.textContent==='Retry').click());assert.equal(calls,2);assert.match(failed.host.textContent,/My Students/);}finally{await failed.close();}
});
