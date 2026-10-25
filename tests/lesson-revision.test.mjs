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
 Component=(await server.ssrLoadModule('/src/pages/professor/components/LessonRevision.jsx')).default;
 ({ProfessorWorkflowContext:Context}=await server.ssrLoadModule('/src/context/professor/useProfessorWorkflow.js'));
});
after(async()=>{await server.close();dom.window.close();});
const initial={version:0,revisionNumber:1,state:'DRAFT',lessonText:'Published text',aiNotes:'Notes',youtubeLink:'',files:[{key:'old',originalFileName:'Original.pdf'}]};
async function mount(api,preview=false){const host=document.createElement('div');document.body.append(host);const root=createRoot(host);let published=0;await act(async()=>root.render(React.createElement(Context.Provider,{value:{api,preview}},React.createElement(Component,{moduleId:41,onPublished:()=>published++}))));return{host,published:()=>published,close:async()=>{await act(async()=>root.unmount());host.remove();}};}
const button=(label)=>[...document.querySelectorAll('button')].find(b=>b.textContent===label);
const click=async label=>act(async()=>button(label).click());
test('published revision requires confirmation, save and review before publication',async()=>{
 const calls=[];const api={getRevision:async()=>null,startRevision:async()=>{calls.push('start');return initial;},saveRevision:async(id,data,files)=>{calls.push('save');assert.equal(id,41);assert.deepEqual(data.keepFiles,['old']);assert.equal(files.length,0);return{...initial,...data.materials,version:1};},reviewRevision:async(id,version)=>{calls.push('review');assert.equal(version,1);return{...initial,lessonText:'New text',version:2,state:'REVIEWED',generatedKnowledge:'New knowledge'};},publishRevision:async(id,version)=>{calls.push('publish');assert.equal(version,2);}};
 const ui=await mount(api);try{
  await click('Revise Lesson');assert.match(document.body.textContent,/Revise Published Lesson\?/);assert.equal(calls.length,0);await click('Cancel');assert.equal(calls.length,0);
  await click('Revise Lesson');await click('Create Revision');assert.equal(ui.host.querySelector('textarea').value,'Published text');assert.match(ui.host.textContent,/Students are still seeing/);assert.equal(button('Publish Revision').disabled,true);
  await act(async()=>{const input=ui.host.querySelector('textarea');Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype,'value').set.call(input,'New text');input.dispatchEvent(new Event('input',{bubbles:true}));});
  assert.equal(button('Review Changes').disabled,true);await click('Save Draft');await click('Review Changes');assert.match(ui.host.textContent,/New knowledge/);
  await click('Publish Revision');await act(async()=>[...document.querySelectorAll('[role="dialog"] button')].find(b=>b.textContent==='Publish Revision').click());assert.equal(ui.published(),1);assert.deepEqual(calls,['start','save','review','publish']);
 }finally{await ui.close();}
});
test('preview has no revision writes and failed review leaves draft editable',async()=>{
 let called=0;const hidden=await mount({getRevision:async()=>{called++;}},true);try{assert.equal(hidden.host.textContent,'');assert.equal(called,0);}finally{await hidden.close();}
 const ui=await mount({getRevision:async()=>initial,reviewRevision:async()=>{throw{response:{status:422,data:{code:'MATERIAL_NOT_RELEVANT'}}};},discardRevision:async()=>{called++;}});try{
  await click('Review Changes');assert.match(ui.host.textContent,/does not appear to match/);assert.equal(button('Publish Revision').disabled,true);assert.equal(ui.host.querySelector('textarea').value,'Published text');
  await click('Discard Revision');await act(async()=>[...document.querySelectorAll('[role="dialog"] button')].find(b=>b.textContent==='Discard Revision').click());assert.equal(called,1);assert.ok(button('Revise Lesson'));
 }finally{await ui.close();}
});
