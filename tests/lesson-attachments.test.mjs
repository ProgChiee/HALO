import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'vite';
import { JSDOM } from 'jsdom';
import React, { act } from 'react';
let dom, server, createRoot, Attachments, client, services, scopes, created=[], revoked=[];
before(async()=>{
 dom=new JSDOM('<html><body></body></html>',{url:'http://localhost'});for(const k of ['window','document','HTMLElement','Event','KeyboardEvent','localStorage','sessionStorage'])globalThis[k]=dom.window[k];globalThis.IS_REACT_ACT_ENVIRONMENT=true;
 URL.createObjectURL=()=>{const url='blob:material-'+created.length;created.push(url);return url;};URL.revokeObjectURL=url=>revoked.push(url);
 ({createRoot}=await import('react-dom/client'));server=await createServer({server:{middlewareMode:true},appType:'custom'});
 Attachments=(await server.ssrLoadModule('/src/pages/student/components/LessonAttachments.jsx')).default;
 client=(await server.ssrLoadModule('/src/services/apiClient.js')).default;services=await server.ssrLoadModule('/src/services/student/studentService.js');scopes=await server.ssrLoadModule('/src/services/admin/studentModeService.js');
});
after(async()=>{await server.close();dom.window.close();});
const metadata=[{id:5,originalFileName:'Lecture.pdf',fileType:'application/pdf',filePath:'https://must-never-open.example/private'}];
async function mount(load,files=metadata){const host=document.createElement('div');document.body.append(host);const root=createRoot(host);await act(async()=>root.render(React.createElement(Attachments,{files,load})));return async()=>{await act(async()=>root.unmount());host.remove();};}
const click=button=>act(async()=>button.click());const button=label=>[...document.querySelectorAll('button')].find(b=>b.textContent===label);
test('PDF preview fetches by ID, traps focus, closes with Escape and releases its blob',async()=>{
 let calls=0;const close=await mount(async(id,config)=>{calls++;assert.equal(id,5);assert.equal(config.params.download,false);return new Blob(['%PDF-test'],{type:'application/pdf'});});
 try {const opener=button('Preview');opener.focus();await click(opener);assert.equal(calls,1);const frame=document.querySelector('iframe');assert.ok(frame.src.startsWith('blob:'));assert.equal(frame.title,'PDF preview: Lecture.pdf');const url=frame.src;
 assert.equal(document.body.style.overflow,'hidden');await click(document.querySelector('[role=dialog]').parentElement);assert.ok(document.querySelector('[role=dialog]'));
 await act(async()=>document.querySelector('[role=dialog]').dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true})));assert.equal(document.querySelector('iframe'),null);assert.equal(document.activeElement,opener);assert.ok(revoked.includes(url));
 }finally{await close();}
});
test('PNG and JPEG previews render only authenticated blob data',async()=>{for(const type of ['image/png','image/jpeg']){const close=await mount(async()=>new Blob(['image'],{type}),[{id:8,originalFileName:'Diagram',fileType:type}]);try{await click(button('Preview'));assert.ok(document.querySelector('img').src.startsWith('blob:'));assert.equal(document.querySelector('img').alt,'Diagram');assert.equal(document.querySelector('iframe'),null);await click(button('Close preview'));}finally{await close();}}});
test('download uses fresh authorization and a safe filename without navigating to storage',async()=>{
 let link,calls=0;const prevent=event=>{if(event.target.tagName==='A'){link={href:event.target.href,name:event.target.download};event.preventDefault();}};document.body.addEventListener('click',prevent);
 const close=await mount(async(id,config)=>{calls++;assert.equal(id,5);assert.equal(config.params.download,true);return new Blob(['pdf'],{type:'application/pdf'});});
 try {await click(button('Download'));assert.equal(calls,1);assert.equal(link.name,'Lecture.pdf');assert.ok(link.href.startsWith('blob:'));assert.equal(document.querySelector('a'),null);}finally{await close();document.body.removeEventListener('click',prevent);}assert.ok(revoked.includes(link.href));
});
test('pending duplicate requests and stale responses are suppressed',async()=>{let finish,signal,calls=0;const close=await mount((_id,config)=>{calls++;signal=config.signal;return new Promise(resolve=>finish=resolve);});await click(button('Preview'));assert.ok(button('Preview').disabled);await click(button('Download'));assert.equal(calls,1);const count=created.length;await close();assert.equal(signal.aborted,true);await act(async()=>finish(new Blob(['x'],{type:'image/png'})));assert.equal(created.length,count);});
test('denied, missing, network and invalid response errors remain retryable',async()=>{for(const failure of [{response:{status:403}},{response:{status:404}},Error('offline'),null]){const close=await mount(async()=>{if(failure)throw failure;return new Blob(['html'],{type:'text/html'});});try{await click(button('Preview'));assert.ok(document.querySelector('[role=alert]'));assert.equal(document.querySelector('[role=dialog]'),null);assert.ok(!button('Preview').disabled);}finally{await close();}}});
test('empty attachments make no request and existing mode adapters retain target session headers',async()=>{let calls=0;const close=await mount(async()=>calls++,[]);assert.equal(document.querySelector('section'),null);assert.equal(calls,0);await close();const requests=[];client.defaults.adapter=async config=>{requests.push(config);return{data:new Blob(['pdf'],{type:'application/pdf'}),config,status:200,headers:{}};};await services.createStudentService().getLessonAttachment(5);for(const preview of [true,false]){const scoped=scopes.createActingStudentScope('synthetic-session',()=>{},preview);await scoped.api.getLessonAttachment(5);scoped.dispose();}assert.deepEqual(requests.map(r=>r.url),['/student/ai-learning-modules/files/5/content','/admin/preview/student/ai-learning-modules/files/5/content','/admin/acting/student/ai-learning-modules/files/5/content']);assert.ok(requests.every(r=>r.responseType==='blob'));assert.ok(requests.slice(1).every(r=>r.headers['X-Acting-Session']==='synthetic-session'));});
