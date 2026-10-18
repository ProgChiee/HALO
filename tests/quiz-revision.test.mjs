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
async function mount(api,preview=false){const host=document.createElement('div');document.body.append(host);const root=createRoot(host);await act(async()=>root.render(React.createElement(Context.Provider,{value:{api,preview}},React.createElement(Component,{moduleId:41}))));return{host,close:async()=>{await act(async()=>root.unmount());host.remove();}};}
const button=label=>[...document.querySelectorAll('button')].find(b=>b.textContent===label);
const click=async label=>act(async()=>button(label).click());
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
