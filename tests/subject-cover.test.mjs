import {test,before,after} from 'node:test';
import assert from 'node:assert/strict';import {readFile} from 'node:fs/promises';import {JSDOM} from 'jsdom';import {createServer} from 'vite';import React,{act} from 'react';
let dom,server,createRoot,Cover,Editor,apiClient,professorService,studentService,urls=0,revoked=[];
before(async()=>{dom=new JSDOM('<html><body></body></html>',{url:'http://localhost'});for(const k of ['window','document','HTMLElement','Event','KeyboardEvent'])globalThis[k]=dom.window[k];globalThis.IS_REACT_ACT_ENVIRONMENT=true;URL.createObjectURL=()=> 'blob:cover-'+(++urls);URL.revokeObjectURL=url=>revoked.push(url);({createRoot}=await import('react-dom/client'));server=await createServer({server:{middlewareMode:true},appType:'custom'});Cover=(await server.ssrLoadModule('/src/components/shared/SubjectCover.jsx')).default;Editor=(await server.ssrLoadModule('/src/pages/professor/components/SubjectCoverDialog.jsx')).default;apiClient=(await server.ssrLoadModule('/src/services/apiClient.js')).default;professorService=(await server.ssrLoadModule('/src/services/professor/professorService.js')).createProfessorService;studentService=(await server.ssrLoadModule('/src/services/student/studentService.js')).createStudentService;});
after(async()=>{await server.close();dom.window.close();});
async function mount(Component,props){const host=document.createElement('div');document.body.append(host);const root=createRoot(host);await act(async()=>root.render(React.createElement(Component,props)));return {host,root,close:async()=>{await act(async()=>root.unmount());host.remove();}};}
const button=text=>[...document.querySelectorAll('button')].find(b=>b.textContent===text);const click=element=>act(async()=>element.click());
async function choose(file){const input=document.querySelector('input[type=file]');Object.defineProperty(input,'files',{configurable:true,value:[file]});await act(async()=>input.dispatchEvent(new Event('change',{bubbles:true})));}
const blob=()=>new Blob(['synthetic'],{type:'image/png'});
test('missing/null cover uses the exact supplied watermark and custom image errors restore it',async()=>{let ui=await mount(Cover,{subjectId:7});assert.equal(ui.host.querySelector('img').getAttribute('src'),'/image/halo-subject-default.png');assert.equal(ui.host.querySelector('img').alt,'');await ui.close();ui=await mount(Cover,{subjectId:7,load:async()=>blob()});try{assert.equal(ui.host.firstChild.dataset.subjectCover,'custom');await act(async()=>ui.host.querySelector('img').dispatchEvent(new Event('error')));assert.equal(ui.host.firstChild.dataset.subjectCover,'default');}finally{await ui.close();}assert.ok(revoked.length);});
test('204/no-image and read failures retain the default without blocking academic UI',async()=>{for(const load of [async()=>null,async()=>{throw new Error('unavailable');}]){const ui=await mount(Cover,{subjectId:7,load});try{assert.equal(ui.host.firstChild.dataset.subjectCover,'default');}finally{await ui.close();}}});
test('changing target cancels old cover responses and never displays their blob',async()=>{let finish,signal;const load=(_id,c)=>{signal=c.signal;return new Promise(r=>finish=r);};const ui=await mount(Cover,{subjectId:7,load});try{await act(async()=>ui.root.render(React.createElement(Cover,{subjectId:8,load:async()=>null})));assert.equal(signal.aborted,true);await act(async()=>finish(blob()));assert.equal(ui.host.firstChild.dataset.subjectCover,'default');}finally{await ui.close();}});
test('editor previews optional upload, prevents duplicate saves, and retains errors',async()=>{let resolve,calls=0,saved=0;const api={getSubjectCover:async()=>blob(),saveSubjectCover:async(id,file)=>{assert.equal(id,7);assert.equal(file.name,'cover.webp');calls++;await new Promise(r=>resolve=r);},removeSubjectCover:async()=>{}};const ui=await mount(Editor,{subject:{id:7,subjectName:'Synthetic subject'},api,onClose(){},onSaved(){saved++;}});try{assert.ok(button('Save cover').disabled);await choose(new dom.window.File(['test'],'cover.webp',{type:'image/webp'}));assert.ok(!button('Save cover').disabled);await click(button('Save cover'));assert.ok(button('Saving...').disabled);await click(button('Saving...'));assert.equal(calls,1);await act(async()=>resolve());assert.equal(saved,1);}finally{await ui.close();}});
test('editor rejects invalid and oversized choices; remove is separate from Cancel',async()=>{let removed=0,cancelled=0;const api={getSubjectCover:async()=>blob(),removeSubjectCover:async id=>{assert.equal(id,7);removed++;},saveSubjectCover:async()=>{throw Error('Unexpected upload');}};const ui=await mount(Editor,{subject:{id:7,subjectName:'Synthetic subject'},api,onClose(){cancelled++;},onSaved(){}});try{await choose(new dom.window.File(['bad'],'bad.svg',{type:'image/svg+xml'}));assert.match(document.querySelector('[role=alert]').textContent,/JPEG, PNG or WebP/);await choose({type:'image/png',size:3*1024*1024+1});assert.ok(button('Save cover').disabled);await click(button('Cancel'));assert.equal(cancelled,1);assert.equal(removed,0);await click(button('Remove Image'));assert.equal(removed,1);}finally{await ui.close();}});
test('editor has explicit dismissal, focus containment, scroll lock and useful failure feedback',async()=>{let cancelled=0;const opener=document.createElement('button');document.body.append(opener);opener.focus();const api={getSubjectCover:async()=>blob(),removeSubjectCover:async()=>{throw {response:{status:503}};}};const ui=await mount(Editor,{subject:{id:7,subjectName:'Long '.repeat(30)},api,onClose(){cancelled++;},onSaved(){}});try{const dialog=document.querySelector('[role=dialog]');assert.equal(dialog.getAttribute('aria-modal'),'true');await click(dialog.parentElement);await act(async()=>dialog.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true})));assert.equal(cancelled,0);await act(async()=>opener.focus());assert.ok(dialog.contains(document.activeElement));assert.equal(document.body.style.overflow,'hidden');await click(button('Remove Image'));assert.match(document.querySelector('[role=alert]').textContent,/Could not confirm/);assert.ok(!button('Cancel').disabled);}finally{await ui.close();}assert.equal(document.activeElement,opener);opener.remove();});
test('normal and mode adapters keep cover reads scoped; Preview cannot upload',async()=>{globalThis.localStorage=dom.window.localStorage;globalThis.sessionStorage=dom.window.sessionStorage;const calls=[];apiClient.defaults.adapter=async c=>{calls.push(c);return{status:204,config:c,headers:{},data:null};};await professorService().getSubjectCover(7);await studentService().getSubjectCover(7);const {createActingProfessorScope}=await server.ssrLoadModule('/src/services/admin/professorModeService.js');const {createActingStudentScope}=await server.ssrLoadModule('/src/services/admin/studentModeService.js');for(const preview of [false,true]){const p=createActingProfessorScope('synthetic-session',()=>{},preview),s=createActingStudentScope('synthetic-session',()=>{},preview);await p.api.getSubjectCover(7);await s.api.getSubjectCover(7);if(preview)await assert.rejects(()=>p.api.saveSubjectCover(7,new Blob(['x'])),/read-only/);p.dispose();s.dispose();}assert.equal(calls.length,6);assert.ok(calls.slice(2).every(c=>c.headers['X-Acting-Session']==='synthetic-session'));assert.equal(calls[0].url,'/professor/subjects/7/cover');assert.equal(calls[1].url,'/student/subjects/7/cover');});
test('cover styles preserve white watermark, contain/cover, and responsive ratio',async()=>{const css=await readFile(new URL('../src/components/shared/SubjectCover.module.css',import.meta.url),'utf8');assert.match(css,/aspect-ratio: 16 \/ 9/);assert.match(css,/object-fit: contain/);assert.match(css,/object-fit: cover/);assert.match(css,/background: #fff/);const asset=await readFile(new URL('../public/image/halo-subject-default.png',import.meta.url));assert.equal(asset[0],137);for(const page of ['professor/components/SubjectManagement','student/components/EnrollmentSubjects','student/components/Subjects','student/components/CurriculumPreview'])assert.match(await readFile(new URL('../src/pages/'+page+'.jsx',import.meta.url),'utf8'),/<SubjectCover /);});
async function dropFiles(files,type='drop') {
 const zone=document.querySelector('[aria-label="Subject cover uploader"]');
 const event=new Event(type,{bubbles:true,cancelable:true});
 Object.defineProperty(event,'dataTransfer',{value:{files,types:['Files'],dropEffect:'copy'}});
 await act(async()=>zone.dispatchEvent(event));
 assert.equal(event.defaultPrevented,true);
}

test('custom picker is labeled, keyboard reachable and opens the native input without uploading',async()=>{
 let uploads=0,pickers=0;const api={getSubjectCover:async()=>null,saveSubjectCover:async()=>uploads++};
 const ui=await mount(Editor,{subject:{id:7,subjectName:'Synthetic subject'},api,onClose(){},onSaved(){}});
 try {
  const input=document.querySelector('input[type=file]'),picker=button('Choose Image');
  input.addEventListener('click',event=>{event.preventDefault();pickers++;});
  assert.equal(document.activeElement,picker);assert.equal(picker.type,'button');
  assert.equal(document.querySelector(`label[for="${input.id}"]`).textContent,'Subject cover image');
  assert.match(document.getElementById(input.getAttribute('aria-describedby')).textContent,/Maximum 3 MB/);
  assert.match(document.querySelector('[role=dialog]').textContent,/Upload a subject cover/);
  assert.match(document.querySelector('[role=dialog]').textContent,/Drag and drop an image here, or choose a file/);
  assert.equal(document.querySelector('img').getAttribute('src'),'/image/halo-subject-default.png');
  await click(picker);assert.equal(pickers,1);assert.equal(uploads,0);assert.ok(button('Save cover').disabled);
  await act(async()=>picker.dispatchEvent(new KeyboardEvent('keydown',{key:'Tab',bubbles:true})));
  assert.equal(document.activeElement,button('Cancel'));
 } finally {await ui.close();}
});

test('drop selects a draft preview and removing it restores the saved cover without a delete request',async()=>{
 let uploads=0,deletes=0;const api={getSubjectCover:async()=>blob(),saveSubjectCover:async()=>uploads++,removeSubjectCover:async()=>deletes++};
 const ui=await mount(Editor,{subject:{id:7,subjectName:'Synthetic subject'},api,onClose(){},onSaved(){}});
 try {
  const saved=document.querySelector('img').getAttribute('src');
  const file=new dom.window.File(['image'],'long-cover-name.png',{type:'image/png'});
  await dropFiles([file],'dragenter');await dropFiles([file],'dragover');await dropFiles([file]);
  const preview=document.querySelector('img[alt="Selected subject cover preview"]');
  assert.ok(preview.src.startsWith('blob:'));const draft=preview.getAttribute('src');
  assert.match(document.querySelector('[role=status]').textContent,/New image selected.*long-cover-name.png.*Save cover/s);
  assert.ok(button('Change Image'));assert.equal(uploads,0);assert.equal(deletes,0);
  await click(button('Remove Image'));
  assert.equal(document.querySelector('img[alt="Selected subject cover preview"]'),null);
  assert.equal(document.querySelector('img').getAttribute('src'),saved);
  assert.equal(document.querySelector('[role=status]').textContent,'Saved cover');
  assert.ok(revoked.includes(draft));assert.equal(deletes,0);assert.ok(button('Save cover').disabled);
 } finally {await ui.close();}
});

test('replacement revokes previous previews; invalid and empty files preserve the selected draft',async()=>{
 const api={getSubjectCover:async()=>blob()};const ui=await mount(Editor,{subject:{id:7,subjectName:'Synthetic subject'},api,onClose(){},onSaved(){}});
 try {
  await choose(new dom.window.File(['image'],'first.jpg',{type:'image/jpeg'}));const first=document.querySelector('img[alt="Selected subject cover preview"]').getAttribute('src');
  await choose(new dom.window.File(['image'],'second.webp',{type:'image/webp'}));const second=document.querySelector('img[alt="Selected subject cover preview"]').getAttribute('src');
  assert.notEqual(first,second);assert.ok(revoked.includes(first));
  for(const file of [new dom.window.File([],'empty.png',{type:'image/png'}),new dom.window.File(['bad'],'bad.txt',{type:'text/plain'}),{type:'image/png',size:3*1024*1024+1}]){
   await dropFiles([file]);assert.ok(document.querySelector('[role=alert]'));assert.equal(document.querySelector('img[alt="Selected subject cover preview"]').getAttribute('src'),second);assert.ok(!button('Save cover').disabled);
  }
  await dropFiles([new dom.window.File(['x'],'a.png',{type:'image/png'}),new dom.window.File(['x'],'b.png',{type:'image/png'})]);assert.match(document.querySelector('[role=alert]').textContent,/one cover image/);
  await ui.close();assert.ok(revoked.includes(second));
 } catch(error) {await ui.close();throw error;}
});

test('unreadable draft image reports an error and restores the existing saved cover',async()=>{
 const ui=await mount(Editor,{subject:{id:7,subjectName:'Synthetic subject'},api:{getSubjectCover:async()=>blob()},onClose(){},onSaved(){}});
 try {
  const saved=document.querySelector('img').getAttribute('src');await choose(new dom.window.File(['invalid bytes'],'broken.png',{type:'image/png'}));
  await act(async()=>document.querySelector('img[alt="Selected subject cover preview"]').dispatchEvent(new Event('error')));
  assert.match(document.querySelector('[role=alert]').textContent,/could not be opened/);assert.equal(document.querySelector('img').getAttribute('src'),saved);assert.ok(button('Save cover').disabled);
 } finally {await ui.close();}
});

test('Cancel discards a draft; failed uploads retain it for retry and block drops while pending',async()=>{
 let reject,calls=0,cancelled=0;const api={getSubjectCover:async()=>null,saveSubjectCover:async(_id,file)=>{assert.equal(file.name,'draft.png');calls++;await new Promise((_resolve,r)=>reject=r);}};
 const ui=await mount(Editor,{subject:{id:7,subjectName:'Synthetic subject'},api,onClose(){cancelled++;},onSaved(){assert.fail('Failed upload must not show success');}});
 try {
  await choose(new dom.window.File(['image'],'draft.png',{type:'image/png'}));await click(button('Save cover'));
  assert.ok(button('Change Image').disabled);assert.ok(button('Cancel').disabled);
  await dropFiles([new dom.window.File(['image'],'other.webp',{type:'image/webp'})]);await click(button('Saving...'));assert.equal(calls,1);
  await act(async()=>reject({response:{status:400}}));assert.match(document.querySelector('[role=alert]').textContent,/valid JPEG/);
  assert.match(document.querySelector('[role=status]').textContent,/draft.png/);assert.ok(!button('Save cover').disabled);
  await click(button('Cancel'));assert.equal(cancelled,1);assert.equal(calls,1);
 } finally {await ui.close();}
});

test('uploader keeps hidden input, theme-aware focus and mobile layouts scoped to the cover dialog',async()=>{
 const css=await readFile(new URL('../src/pages/professor/styles/SubjectCoverDialog.module.css',import.meta.url),'utf8');
 assert.match(css,/clip-path: inset\(50%\)/);assert.match(css,/aspect-ratio: 16 \/ 9/);assert.match(css,/:focus-visible/);assert.match(css,/var\(--color-surface\)/);assert.match(css,/@media\(max-width:480px\)/);assert.match(css,/overflow-wrap: anywhere/);
});
