import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';
let dom,server,createRoot,Component,Context;
before(async()=>{
 dom=new JSDOM('<html><body></body></html>',{url:'http://localhost'});
 for(const key of ['window','document','HTMLElement','Event','localStorage','sessionStorage'])globalThis[key]=dom.window[key];
 globalThis.IS_REACT_ACT_ENVIRONMENT=true;({createRoot}=await import('react-dom/client'));
 server=await createServer({server:{middlewareMode:true},appType:'custom'});
 Component=(await server.ssrLoadModule('/src/pages/professor/components/QuizRevision.jsx')).default;
 ({ProfessorWorkflowContext:Context}=await server.ssrLoadModule('/src/context/professor/useProfessorWorkflow.js'));
});
after(async()=>{await server.close();dom.window.close();});
const question={number:1,question:'Latest published topic?',optionA:'First',optionB:'Second',optionC:'Third',optionD:'Fourth',correctAnswer:'B'};
const current={id:1,version:1,questions:[{...question,question:'Old topic?'}]};
const draft={id:2,version:2,questions:[question]};
async function mount(api,preview=false){const host=document.createElement('div');document.body.append(host);const root=createRoot(host);const render=async(moduleId=41)=>act(async()=>root.render(React.createElement(Context.Provider,{value:{api,preview}},React.createElement(Component,{moduleId}))));await render();return{host,render,close:async()=>{await act(async()=>root.unmount());host.remove();}};}
const button=label=>[...document.querySelectorAll('button')].find(b=>b.textContent===label);
const click=async label=>act(async()=>button(label).click());
test('failed initial assessment load offers a local retry',async()=>{
 let calls=0;const ui=await mount({getQuizRevision:async()=>{calls++;throw{response:{status:503,data:{}}};}});
 try{
  assert.equal(calls,1);assert.ok(ui.host.querySelector('[role="alert"]'));
  assert.ok(button('Retry'),'Failed assessment load should offer a Retry button');
  assert.equal(button('Retry').disabled,false);
 }finally{await ui.close();}
});
test('successful initial load displays the assessment without a retry control',async()=>{
 const calls=[];const ui=await mount({getQuizRevision:async(id,config)=>{calls.push({id,...config});return{current};}});
 try{assert.equal(calls.length,1);assert.equal(calls[0].id,41);assert.equal(calls[0].signal.aborted,false);assert.match(ui.host.textContent,/Current quiz: Version 1/);assert.equal(button('Retry'),undefined);assert.equal(ui.host.querySelector('[role="alert"]'),null);}
 finally{await ui.close();assert.equal(calls[0].signal.aborted,true);}
});
test('retry clears errors, disables repeated clicks, and renders the latest assessment',async()=>{
 let resolve;const calls=[];const ui=await mount({getQuizRevision:(id,config)=>{calls.push({id,...config});return calls.length===1?Promise.reject({response:{status:503}}):new Promise(done=>{resolve=done;});}});
 try{
  const retry=button('Retry');retry.focus();assert.equal(document.activeElement,retry);assert.equal(retry.type,'button');
  await act(async()=>{retry.click();retry.click();retry.click();});
  assert.equal(calls.length,2);assert.equal(calls[1].id,41);assert.equal(button('Retry').disabled,true);
  assert.equal(ui.host.querySelector('[role="alert"]'),null);assert.match(ui.host.querySelector('[role="status"]').textContent,/Loading assessment/);
  await click('Retry');assert.equal(calls.length,2);
  await act(async()=>resolve({needsReview:true,current,draft}));
  assert.equal(button('Retry'),undefined);assert.equal(ui.host.querySelector('[role="status"]'),null);assert.equal(ui.host.querySelector('[role="alert"]'),null);
  assert.match(ui.host.textContent,/Draft quiz: Version 2/);await click('Review Existing Quiz');assert.match(ui.host.textContent,/Old topic/);
  await click('Review Quiz Draft');assert.match(ui.host.textContent,/Latest published topic/);assert.equal(button('Approve Quiz').disabled,false);
 }finally{await ui.close();}
});
test('failed retry keeps a useful error and allows a later manual retry without automatic requests',async()=>{
 let calls=0;const ui=await mount({getQuizRevision:async()=>{calls++;if(calls<3)throw{code:'ERR_NETWORK'};return{current};}});
 try{
  await click('Retry');assert.equal(calls,2);assert.match(ui.host.querySelector('[role="alert"]').textContent,/Cannot reach the server/);assert.equal(button('Retry').disabled,false);
  await act(async()=>new Promise(done=>setTimeout(done,10)));assert.equal(calls,2);
  await click('Retry');assert.equal(calls,3);assert.equal(ui.host.querySelector('[role="alert"]'),null);assert.match(ui.host.textContent,/Current quiz: Version 1/);
 }finally{await ui.close();}
});
test('unauthorized retry preserves safe access errors and never exposes assessment actions',async()=>{
 for(const status of [401,403]){
  let calls=0;const ui=await mount({getQuizRevision:async id=>{assert.equal(id,41);calls++;throw{response:{status,data:{message:'PRIVATE_DETAIL'}}};}});
  try{await click('Retry');assert.equal(calls,2);assert.match(ui.host.querySelector('[role="alert"]').textContent,status===401?/sign in/:/permission/);assert.doesNotMatch(ui.host.textContent,/PRIVATE_DETAIL/);assert.equal(button('Regenerate Quiz from Latest Lesson'),undefined);assert.equal(button('Retry').disabled,false);}
  finally{await ui.close();}
 }
});
test('changing modules aborts a pending retry and ignores its late response',async()=>{
 let resolve;const calls=[];const ui=await mount({getQuizRevision:(id,config)=>{calls.push({id,...config});if(calls.length===1)return Promise.reject({response:{status:503}});if(id===41)return new Promise(done=>{resolve=done;});return Promise.resolve({current:{...current,version:42}});}});
 try{
  await click('Retry');assert.equal(calls.length,2);await ui.render(42);assert.equal(calls[1].signal.aborted,true);assert.equal(calls[2].id,42);
  await act(async()=>resolve({current}));assert.match(ui.host.textContent,/Current quiz: Version 42/);assert.doesNotMatch(ui.host.textContent,/Current quiz: Version 1/);assert.equal(calls.length,3);
 }finally{await ui.close();}
});
test('unmount aborts the retry request',async()=>{
 let resolve,signal,calls=0;const ui=await mount({getQuizRevision:(id,config)=>{calls++;if(calls===1)return Promise.reject({response:{status:503}});signal=config.signal;return new Promise(done=>{resolve=done;});}});
 await click('Retry');await ui.close();assert.equal(signal.aborted,true);await act(async()=>resolve({current}));assert.equal(calls,2);
});
test('revision notice, existing quiz review, generation and explicit approval remain separate',async()=>{
 const calls=[];const ui=await mount({getQuizRevision:async()=>({needsReview:true,current}),generateQuizRevision:async id=>{assert.equal(id,41);calls.push('generate');return{needsReview:true,current,draft};},approveQuizRevision:async(id,draftId)=>{assert.equal(id,41);assert.equal(draftId,2);calls.push('approve');return{needsReview:false,current:draft};}});
 try{
  assert.match(ui.host.textContent,/Assessment review needed/);await click('Review Existing Quiz');assert.match(ui.host.textContent,/Old topic/);
  await click('Regenerate Quiz from Latest Lesson');assert.deepEqual(calls,[]);await click('Cancel');assert.deepEqual(calls,[]);
  await click('Regenerate Quiz from Latest Lesson');await click('Generate Draft');assert.deepEqual(calls,['generate']);assert.match(ui.host.textContent,/Current quiz: Version 1/);assert.match(ui.host.textContent,/not available to Students/);assert.match(ui.host.textContent,/Latest published topic/);
  await click('Close Review');assert.equal(button('Approve Quiz').disabled,true);await click('Review Quiz Draft');assert.equal(button('Approve Quiz').disabled,false);
  await click('Approve Quiz');assert.deepEqual(calls,['generate']);await click('Approve Quiz Version');assert.deepEqual(calls,['generate','approve']);assert.match(ui.host.textContent,/Current quiz: Version 2/);assert.doesNotMatch(ui.host.textContent,/Assessment review needed/);
 }finally{await ui.close();}
});
test('Preview has no quiz writes; stale approval error retains current quiz and draft',async()=>{
 let called=0;const hidden=await mount({getQuizRevision:async()=>{called++;}},true);try{assert.equal(called,0);assert.equal(hidden.host.textContent,'');}finally{await hidden.close();}
 const ui=await mount({getQuizRevision:async()=>({needsReview:true,current,draft}),approveQuizRevision:async()=>{throw{response:{status:409,data:{code:'STALE_MODULE_OPERATION'}}};}});
 try{await click('Review Quiz Draft');await click('Approve Quiz');await click('Approve Quiz Version');assert.match(document.body.textContent,/Reload/);assert.match(ui.host.textContent,/Current quiz: Version 1/);assert.match(ui.host.textContent,/Draft quiz: Version 2/);await click('Cancel');}finally{await ui.close();}
});
