import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';

let dom, server, createRoot, Router, AuthContext, Workflow, Dashboard;
before(async () => {
  dom = new JSDOM('<html><body></body></html>', { url: 'http://localhost' });
  for (const key of ['window', 'document', 'HTMLElement', 'Event', 'localStorage', 'sessionStorage']) globalThis[key] = dom.window[key];
  globalThis.IS_REACT_ACT_ENVIRONMENT = true;
  window.matchMedia = () => ({ matches: false, addEventListener() {}, removeEventListener() {} });
  ({ createRoot } = await import('react-dom/client'));
  Router = await import('react-router-dom');
  server = await createServer({ server: { middlewareMode: true }, appType: 'custom' });
  ({ AuthContext } = await server.ssrLoadModule('/src/context/login/useAuth.js'));
  ({ StudentWorkflowContext: Workflow } = await server.ssrLoadModule('/src/context/student/useStudentWorkflow.js'));
  Dashboard = (await server.ssrLoadModule('/src/pages/student/components/StudentDashboard.jsx')).default;
});
after(async () => { await server.close(); dom.window.close(); });

test('Dashboard paginates enrolled subjects only without altering authoritative progress', async () => {
  const host=document.createElement('div');document.body.append(host);const root=createRoot(host);
  let calls=0;
  const workflow={basePath:'/student',api:{getDashboardData:async()=>({}),getSubjectWeeks:async()=>[],getSubjectsData:async()=>{
    calls++;return Array.from({length:22},(_,i)=>({subjectId:i,subjectName:`Subject ${i}`,subjectCode:`AHRT${i}`,enrolled:i!==0,progressPercentage:100}));
  }}};
  try{
    await act(async()=>root.render(React.createElement(AuthContext.Provider,{value:{user:{name:'Student'},logout(){}}},
      React.createElement(Workflow.Provider,{value:workflow},React.createElement(Router.MemoryRouter,null,React.createElement(Dashboard))))));
    const nav=host.querySelector('[aria-label="Enrolled Subjects pagination"]');
    assert.match(nav.textContent,/Showing 1–5 of 21 subjects/);
    assert.equal(host.querySelectorAll('progress').length,5);
    await act(async()=>nav.querySelector('[aria-label="Page 5"]').click());
    assert.match(nav.textContent,/Showing 21–21 of 21/);
    assert.equal(host.querySelectorAll('progress').length,1);assert.equal(host.querySelector('progress').value,100);
    const section=host.querySelector('[aria-labelledby="enrolled-subjects-title"]');
    assert.ok(section.contains(nav));
    const search=async value=>act(async()=>{const input=section.querySelector('input[type="search"]');Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value').set.call(input,value);input.dispatchEvent(new Event('input',{bubbles:true}));});
    await search('  ahrt21  ');assert.match(nav.textContent,/Showing 1–1 of 1/);assert.match(section.textContent,/Subject 21/);
    await search('sUBjECT 1');assert.match(nav.textContent,/Showing 1–5 of 11/);
    await search('AHRT0');assert.match(section.textContent,/No subjects found/);assert.match(nav.textContent,/Showing 0–0 of 0/);
    await act(async()=>[...section.querySelectorAll('button')].find(b=>b.textContent==='Clear search').click());
    assert.match(nav.textContent,/Showing 1–5 of 21/);assert.equal(document.activeElement,section.querySelector('input'));
    assert.equal(calls,1);
  }finally{await act(async()=>root.unmount());host.remove();}
});

test('Continue resumes persisted recent module after remount and navigates to its subject/week', async () => {
  const workflow = { acting: false, basePath: '/student', api: {
    getDashboardData: async () => ({}),
    getSubjectsData: async () => [{ subjectId: 1, subjectName: 'Older subject', enrolled: true, progressPercentage: 0 },
      { subjectId: 15, subjectName: 'Leadership', enrolled: true, progressPercentage: 100 }],
    getSubjectWeeks: async id => [{ moduleId: id === 15 ? 42 : 1, weekId: id === 15 ? 18 : 1,
      weekNumber: 1, title: id === 15 ? 'Recent lesson' : 'Older lesson', unlocked: true,
      lessonAvailable: true, completed: id === 15, lastActivityAt: id === 15 ? '2026-10-26T01:00:00' : null }],
  } };
  function Location() { return React.createElement('output', null, Router.useLocation().pathname); }
  for (let mount = 0; mount < 2; mount++) {
    const host = document.createElement('div'); document.body.append(host); const root = createRoot(host);
    try {
      await act(async () => root.render(React.createElement(AuthContext.Provider, { value: { user: { name: 'Test' }, logout() {} } },
        React.createElement(Workflow.Provider, { value: workflow }, React.createElement(Router.MemoryRouter, null,
          React.createElement(Dashboard), React.createElement(Location))))));
      assert.match(host.textContent, /Week 1: Recent lesson/);
      await act(async () => [...host.querySelectorAll('button')].find(b => b.textContent === 'Continue').click());
      assert.equal(host.querySelector('output').textContent, '/student/lesson/15/18');
    } finally { await act(async () => root.unmount()); host.remove(); }
  }
});

for (const [name, score, expected] of [
  ['null', { latestAssessmentScore: null }, 'No assessment yet'],
  ['undefined', { latestAssessmentScore: undefined }, 'No assessment yet'],
  ['missing field', {}, 'No assessment yet'],
  ['actual zero', { latestAssessmentScore: 0 }, '0%'],
  ['positive', { latestAssessmentScore: 85 }, '85%'],
  ['perfect', { latestAssessmentScore: 100 }, '100%'],
]) {
  test(`Dashboard renders ${name} assessment score correctly`, async () => {
    const summary = { completedModules: 2, passedAssessments: 1, totalBadges: 3, ...score };
    const workflow = { acting: false, basePath: '/student', api: {
      getDashboardData: async () => summary,
      getSubjectsData: async () => [],
      getSubjectWeeks: async () => [],
    } };
    const host = document.createElement('div'); document.body.append(host); const root = createRoot(host);
    try {
      await act(async () => root.render(React.createElement(AuthContext.Provider, { value: { user: { name: 'Synthetic Student' }, logout() {} } },
        React.createElement(Workflow.Provider, { value: workflow }, React.createElement(Router.MemoryRouter, null, React.createElement(Dashboard))))));
      const card = label => [...host.querySelectorAll('p')].find(p => p.textContent === label).parentElement;
      assert.equal(card('Latest assessment score').querySelector('p').textContent, expected);
      assert.equal(card('Modules completed').querySelector('p').textContent, '2');
      assert.equal(card('Assessments passed').querySelector('p').textContent, '1');
      assert.equal(card('Badges earned').querySelector('p').textContent, '3');
      assert.deepEqual(summary, { completedModules: 2, passedAssessments: 1, totalBadges: 3, ...score });
    } finally { await act(async () => root.unmount()); host.remove(); }
  });
}

test('Dashboard does not request or offer lessons from unenrolled subject history',async()=>{
 const requested=[];
 const workflow={basePath:'/student',acting:false,api:{getDashboardData:async()=>({completedModules:2}),getSubjectsData:async()=>[{subjectId:15,subjectName:'Unenrolled historical subject',enrolled:false}],getSubjectWeeks:async id=>{requested.push(id);return [];}}};
 const host=document.createElement('div');document.body.append(host);const root=createRoot(host);
 try{
  await act(async()=>root.render(React.createElement(AuthContext.Provider,{value:{user:{name:'Student'},logout(){}}},React.createElement(Workflow.Provider,{value:workflow},React.createElement(Router.MemoryRouter,null,React.createElement(Dashboard))))));
  assert.deepEqual(requested,[]);assert.ok(!host.textContent.includes('Unenrolled historical subject'));assert.ok(![...host.querySelectorAll('button')].some(button=>button.textContent==='Continue'));
 }finally{await act(async()=>root.unmount());host.remove();}
});


test('enrollment changes refresh normal dashboard shortcuts while preserving historical totals and mode isolation',async()=>{
 const {notifyStudentEnrollmentChanged}=await server.ssrLoadModule('/src/utils/studentEnrollmentEvents.js');
 for(const acting of [false,true]){
  let enrolled=true,reads=0;const workflow={acting,preview:acting,target:{name:'Synthetic'},leave(){},basePath:acting?'/admin/student-preview/test':'/student',api:{
   getDashboardData:async()=>({completedModules:2,passedAssessments:2,totalBadges:1,latestAssessmentScore:40}),
   getSubjectsData:async()=>{reads++;return [{subjectId:15,subjectName:'Current enrolled subject',enrolled,progressPercentage:50}];},
   getSubjectWeeks:async()=>[{moduleId:42,weekId:18,weekNumber:1,title:'Current lesson',unlocked:true,lessonAvailable:true}]
  }};
  const host=document.createElement('div');document.body.append(host);const root=createRoot(host);
  try{
   await act(async()=>root.render(React.createElement(AuthContext.Provider,{value:{user:{name:'Synthetic'},logout(){}}},React.createElement(Workflow.Provider,{value:workflow},React.createElement(Router.MemoryRouter,null,React.createElement(Dashboard))))));
   const before=reads;enrolled=false;await act(async()=>notifyStudentEnrollmentChanged());
   assert.equal(reads,before+(acting?0:1));
   if(!acting){assert.ok(!host.textContent.includes('Current enrolled subject'));assert.ok(![...host.querySelectorAll('button')].some(b=>b.textContent==='Continue'));}
   assert.ok(host.textContent.includes('40%'));assert.ok(host.textContent.includes('Assessments passed'));
  }finally{await act(async()=>root.unmount());host.remove();}
 }
});
