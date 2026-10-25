import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';
import { quizLoadError } from '../src/utils/quizLoadError.js';
let dom, server, createRoot, Router, Auth, Toast, Workflow, Quiz;
before(async () => {
  dom = new JSDOM('<html><body></body></html>', { url: 'http://localhost' });
  for (const key of ['window','document','HTMLElement','Event','localStorage','sessionStorage']) globalThis[key] = dom.window[key];
  globalThis.IS_REACT_ACT_ENVIRONMENT = true;
  window.matchMedia = () => ({ matches:false, addEventListener(){}, removeEventListener(){} });
  ({ createRoot } = await import('react-dom/client')); Router = await import('react-router-dom');
  server = await createServer({server:{middlewareMode:true},appType:'custom'});
  ({AuthContext:Auth}=await server.ssrLoadModule('/src/context/login/useAuth.js'));
  ({ToastContext:Toast}=await server.ssrLoadModule('/src/context/notifications/useToast.js'));
  ({StudentWorkflowContext:Workflow}=await server.ssrLoadModule('/src/context/student/useStudentWorkflow.js'));
  Quiz=(await server.ssrLoadModule('/src/pages/student/components/Quiz.jsx')).default;
});
after(async()=>{await server.close();dom.window.close();});
const errors = [
  [{code:'ERR_NETWORK'}, /Check your connection/],
  [{code:'ECONNABORTED'}, /Check your connection/],
  [{response:{status:401}}, /sign in again/],
  [{response:{status:403}}, /do not have access/],
  [{response:{status:404}}, /not available yet/],
  [{response:{status:500}}, /server could not load/],
  [{response:{status:503}}, /server could not load/],
];
test('fixed error messages distinguish HTTP failures from network failures and hide server details',()=>{
  for(const [error,expected] of errors){
    error.message='SECRET SQL';if(error.response)error.response.data={message:'SECRET SQL'};
    assert.match(quizLoadError(error),expected);assert.doesNotMatch(quizLoadError(error),/SECRET/);
  }
  assert.doesNotMatch(quizLoadError(new Error('Unexpected local validation failure')),/connection/);
});
async function mount(getAssessment) {
  let reads=0,writes=0;
  const api={
    getWeekLesson:async()=>({id:3,subjectId:1,weekId:2,weekNumber:1,status:'APPROVED',aiGenerationStatus:'COMPLETED'}),
    getAssessmentStatus:async()=>({assessmentExists:true,assessmentAvailable:true,canTakeAssessment:true}),
    getAssessment:(...args)=>{reads++;return getAssessment(...args);},
    startAttempt:async()=>{writes++;},submitAttempt:async()=>{writes++;},getAttemptResult:async()=>{throw new Error('Unexpected result read');},
  };
  const host=document.createElement('div');document.body.append(host);const root=createRoot(host);
  await act(async()=>root.render(React.createElement(Auth.Provider,{value:{user:{name:'Test'},logout(){}}},
    React.createElement(Toast.Provider,{value:{showToast(){}}},React.createElement(Workflow.Provider,{value:{api,identity:'test',basePath:'/student',acting:false}},
      React.createElement(Router.MemoryRouter,{initialEntries:['/student/quiz/1/2']},React.createElement(Router.Routes,null,
        React.createElement(Router.Route,{path:'/student/quiz/:topicId/:weekId',element:React.createElement(Quiz)}))))))));
  return {host,reads:()=>reads,writes:()=>writes,cleanup:async()=>{await act(async()=>root.unmount());host.remove();}};
}
const assessment={id:4,title:'Synthetic Quiz',passingScore:60,questions:[{id:5,questionText:'Question',optionA:'A',optionB:'B',optionC:'C',optionD:'D'}]};
test('each loading error displays accurate guidance and a keyboard accessible local retry',async()=>{
  for(const [error,expected] of errors){
    const view=await mount(async()=>{throw error;});
    try{assert.match(view.host.querySelector('[role="alert"]').textContent,expected);assert.ok([...view.host.querySelectorAll('button')].some(b=>b.textContent==='Retry'));assert.equal(view.writes(),0);}
    finally{await view.cleanup();}
  }
});
test('retry repeats loading without page reload, blocks duplicate clicks, survives failure then succeeds without quiz writes',async()=>{
  let finish,fail=true;
  const view=await mount(()=>fail?Promise.reject({response:{status:500}}):new Promise(resolve=>{finish=resolve;}));
  try{
    let retry=[...view.host.querySelectorAll('button')].find(b=>b.textContent==='Retry');
    await act(async()=>retry.click());assert.equal(view.reads(),2);assert.ok(view.host.querySelector('[role="alert"]'));
    fail=false;retry=[...view.host.querySelectorAll('button')].find(b=>b.textContent==='Retry');
    await act(async()=>{retry.click();retry.click();retry.click();});
    assert.equal(view.reads(),3);assert.match(view.host.querySelector('[role="status"]').textContent,/Loading quiz/);
    assert.equal([...view.host.querySelectorAll('button')].find(b=>b.textContent==='Retry'),undefined);
    await act(async()=>finish(assessment));assert.equal(view.host.querySelector('[role="alert"]'),null);
    assert.match(view.host.textContent,/Synthetic Quiz/);assert.equal(view.writes(),0);
  }finally{await view.cleanup();}
});
test('initial successful load displays quiz without an error or automatic attempt creation',async()=>{
  const view=await mount(async()=>assessment);
  try{assert.match(view.host.textContent,/Synthetic Quiz/);assert.equal(view.host.querySelector('[role="alert"]'),null);assert.equal(view.reads(),1);assert.equal(view.writes(),0);}
  finally{await view.cleanup();}
});
