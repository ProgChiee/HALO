import {test,before,after} from 'node:test';
import assert from 'node:assert/strict';
import {JSDOM} from 'jsdom';
import {createServer} from 'vite';
import React,{act} from 'react';
let dom,server,createRoot,Router,AuthContext,Workflow,Page,Subjects,api;
const h=React.createElement;
before(async()=>{
 dom=new JSDOM('<html><body></body></html>',{url:'http://localhost'});
 for(const key of ['window','document','HTMLElement','Event','MouseEvent','localStorage','sessionStorage'])globalThis[key]=dom.window[key];
 globalThis.IS_REACT_ACT_ENVIRONMENT=true;
 ({createRoot}=await import('react-dom/client'));Router=await import('react-router-dom');
 server=await createServer({server:{middlewareMode:true},appType:'custom'});
 Page=(await server.ssrLoadModule('/src/pages/student/components/EnrollmentSubjects.jsx')).default;
 Subjects=(await server.ssrLoadModule('/src/pages/student/components/Subjects.jsx')).default;
 ({AuthContext}=await server.ssrLoadModule('/src/context/login/useAuth.js'));
 ({StudentWorkflowContext:Workflow}=await server.ssrLoadModule('/src/context/student/useStudentWorkflow.js'));
 api=(await server.ssrLoadModule('/src/services/apiClient.js')).default;
});
after(async()=>{await server?.close();dom?.window.close();});
const subject={enrolled:true,subjectId:15,subjectCode:'HRM-2222',subjectName:'Hospitality leadership',yearLevel:'FIRST_YEAR',totalWeeks:2,progressPercentage:50};
function CurrentPath(){return h('output',{'data-testid':'path'},Router.useLocation().pathname);}
async function mount({browse=false,acting=false,preview=false,mine=[],available=[subject],loader,legacy=false,mobile=false,weeks=[],legacySubjects=[subject],selectedSubject='15',navigationFlow=false,curriculumLoader,historyEntries}={}){
 window.matchMedia=()=>({matches:mobile,addEventListener(){},removeEventListener(){}});
 const host=document.createElement('div');document.body.append(host);const root=createRoot(host),reads=[];let subjectReads=0;
 const base=acting?`/admin/${preview?'student-preview':'student-mode'}/session`:'/student';
 const workflow={identity:'test',basePath:base,acting,preview,target:{name:'Synthetic target'},leave(){},api:{
  getSubjectCurriculum:async(id,config)=>{reads.push('curriculum:'+id);return curriculumLoader?curriculumLoader(id,config):{subjectId:id,subjectName:'Published curriculum',subjectCode:'HRM101',yearLevel:'FIRST_YEAR',publishedWeekCount:1,weeks:[{weekNumber:1,title:'Published lesson title'}]};},
  getMySubjects:async()=>{reads.push('my');return mine;},getBrowseSubjects:loader??(async()=>{reads.push('browse');return available;}),
  getSubjectsData:async()=>{subjectReads++;return legacySubjects;},getSubjectWeeks:async(id)=>{reads.push(id);return weeks;}
 }};
 let historyNavigate;
 function History(){historyNavigate=Router.useNavigate();return null;}
 function Flow(){return h(Router.Routes,null,
  h(Router.Route,{path:base+'/subjects',element:h(Subjects)}),
  h(Router.Route,{path:base+'/subjects/my',element:h(Page)}),
  h(Router.Route,{path:base+'/subjects/browse',element:h(Page,{browse:true})}),
  h(Router.Route,{path:base+'/lesson/:topicId/:weekId',element:h('p',null,'Selected lesson route')}),
  h(Router.Route,{path:base,element:h('p',null,'Student dashboard route')}));}
 const initial=legacy?`${base}/subjects${selectedSubject===null?'':`?subject=${selectedSubject}`}`:`${base}/subjects/${browse?'browse':'my'}`;
 await act(async()=>root.render(h(Router.MemoryRouter,{initialEntries:historyEntries??[initial]},h(AuthContext.Provider,{value:{user:{name:'Synthetic'},logout(){}}},h(Workflow.Provider,{value:workflow},h(React.Fragment,null,h(navigationFlow||legacy?Flow:Page,{browse}),h(CurrentPath),h(History)))))));
 return {host,reads,get subjectReads(){return subjectReads;},async navigate(destination){await act(async()=>historyNavigate(destination));},async close(){await act(async()=>root.unmount());host.remove();}};
}
const button=(host,text)=>[...host.querySelectorAll('button')].find(x=>x.textContent.trim()===text);
test('subjects submenu auto expands, active child is marked and toggle remains operable',async()=>{
 const ui=await mount();try{const toggle=button(ui.host,'Subjects');assert.equal(toggle.getAttribute('aria-expanded'),'true');assert.ok(ui.host.querySelector('nav a[aria-current="page"][href="/student/subjects/my"]'));await act(async()=>toggle.click());assert.equal(toggle.getAttribute('aria-expanded'),'false');await act(async()=>toggle.click());assert.equal(toggle.getAttribute('aria-expanded'),'true');}finally{await ui.close();}
});
test('My Subjects shows authoritative progress and Continue keeps existing subject flow',async()=>{
 const ui=await mount({mine:[subject]});try{assert.match(ui.host.textContent,/50%/);assert.match(ui.host.textContent,/Enrolled/);assert.equal(ui.host.querySelector('a[href="/student/subjects?subject=15"]').textContent,'Continue Learning');assert.equal(button(ui.host,'Enroll in Subject'),undefined);assert.deepEqual(ui.reads,['my']);}finally{await ui.close();}
 const legacy=await mount({legacy:true});try{assert.ok(legacy.reads.includes(15));}finally{await legacy.close();}
});
test('empty views and failed reads expose helpful recovery',async()=>{
 for(const browse of [false,true]){const ui=await mount({browse,available:[]});try{assert.match(ui.host.textContent,browse?/No subjects are currently available for enrollment/:/You haven't enrolled in any subjects yet/);}finally{await ui.close();}}
 let calls=0;const ui=await mount({browse:true,loader:async()=>{if(++calls===1)throw {response:{status:500}};return [subject];}});try{assert.ok(button(ui.host,'Retry'));await act(async()=>button(ui.host,'Retry').click());assert.match(ui.host.textContent,/Hospitality leadership/);assert.equal(calls,2);}finally{await ui.close();}
});
test('enrollment blocks duplicate clicks, retains safe errors and refreshes available subjects after success',async()=>{
 let resolve,reject,calls=0,rows=[subject];api.defaults.adapter=config=>{calls++;assert.equal(config.method,'post');assert.equal(config.url,'/student/subjects/15/enrollment');return new Promise((yes,no)=>{resolve=yes;reject=no;});};
 const ui=await mount({browse:true,loader:async()=>rows});try{const enroll=button(ui.host,'Enroll in Subject');await act(async()=>{enroll.click();enroll.click();});assert.equal(calls,1);assert.ok(button(ui.host,'Enrolling...').disabled);
 await act(async()=>reject({response:{status:500,data:{message:'private details'}}}));assert.doesNotMatch(ui.host.textContent,/private details/);assert.ok(button(ui.host,'Enroll in Subject'));
 await act(async()=>button(ui.host,'Enroll in Subject').click());rows=[];await act(async()=>resolve({status:200,headers:{},config:{},data:{enrolled:true}}));assert.equal(calls,2);assert.match(ui.host.textContent,/You are enrolled in Hospitality leadership/);assert.equal(button(ui.host,'Enroll in Subject'),undefined);
 }finally{await ui.close();}
});
test('Preview and Support show eligible subjects but never enrollment controls',async()=>{
 for(const preview of [true,false]){const ui=await mount({browse:true,acting:true,preview});try{assert.match(ui.host.textContent,/Hospitality leadership/);assert.match(ui.host.textContent,/Enrollment is read-only/);assert.equal(button(ui.host,'Enroll in Subject'),undefined);assert.ok(ui.host.querySelector(`a[href="/admin/${preview?'student-preview':'student-mode'}/session/subjects/my"]`));}finally{await ui.close();}}
});
test('mobile navigation stays open when toggling Subjects and closes via its explicit control',async()=>{
 const ui=await mount({mobile:true});try{await act(async()=>ui.host.querySelector('[aria-label="Open navigation"]').click());const dialog=document.querySelector('[role="dialog"]');assert.ok(dialog);await act(async()=>button(dialog,'Subjects').click());assert.ok(document.querySelector('[role="dialog"]'));await act(async()=>document.querySelector('[aria-label="Close navigation"]').click());assert.equal(document.querySelector('[role="dialog"]'),null);}finally{await ui.close();}
});

test('metadata uses separate clean values, correct week plurals and omits missing fields',async()=>{
 for(const [count,label] of [[0,'0 weeks'],[1,'1 week'],[2,'2 weeks'],[null,null]]){
  const ui=await mount({mine:[{...subject,totalWeeks:count,subjectCode:count===null?null:'HRM00222',yearLevel:count===null?null:'FIRST_YEAR'}]});
  try{const metadata=ui.host.querySelector('[aria-label="Subject details"]');assert.deepEqual([...metadata.children].map(x=>x.textContent),label?['HRM00222','1st Year',label]:[]);assert.doesNotMatch(metadata.textContent,/\uFFFD|undefined|null/);}finally{await ui.close();}
 }
});
test('eligible Browse data never becomes an Enrolled card or populates an empty My Subjects view',async()=>{
 const mine=await mount({mine:[],available:[subject]});try{assert.equal(mine.host.querySelectorAll('article').length,0);assert.equal(mine.host.querySelector('a[href="/student/subjects"]'),null);assert.doesNotMatch(mine.host.textContent,/View subject list|Open existing learning subjects|enrollment rollout/);}finally{await mine.close();}
 const browse=await mount({browse:true,mine:[{...subject,subjectId:99}],available:[subject]});try{assert.deepEqual(browse.reads,['browse']);assert.equal(browse.host.querySelectorAll('article').length,1);assert.match(browse.host.querySelector('article').textContent,/Available/);assert.doesNotMatch(browse.host.querySelector('article').textContent,/Enrolled/);}finally{await browse.close();}
});
test('card styling uses compact two-column and mobile single-column layout with an escaped bullet',async()=>{
 const {readFile}=await import('node:fs/promises');const css=await readFile(new URL('../src/pages/student/styles/EnrollmentSubjects.module.css',import.meta.url),'utf8');
 assert.match(css,/grid-template-columns: repeat\(2,minmax\(0,1fr\)\)/);assert.match(css,/@media\(max-width:640px\)[\s\S]*grid-template-columns: 1fr/);assert.match(css,/content: "\\2022"/);assert.match(css,/height: 6px/);
 const source=await readFile(new URL('../src/pages/student/components/EnrollmentSubjects.jsx',import.meta.url),'utf8');assert.doesNotMatch(source,/\uFFFD|\u00ef\u00bf\u00bd/);
});

test('exact completion counts control Review Subject without changing learning routes',async()=>{
 for(const [counts,label] of [
  [{totalWeeks:2,completedWeeks:2,eligibleModuleCount:2,progressPercentage:100},'Review Subject'],
  [{totalWeeks:200,completedWeeks:199,eligibleModuleCount:200,progressPercentage:100},'Continue Learning'],
  [{totalWeeks:2,completedWeeks:1,eligibleModuleCount:1,progressPercentage:100},'Continue Learning'],
  [{totalWeeks:0,completedWeeks:0,eligibleModuleCount:0,progressPercentage:0},'Continue Learning'],
  [{totalWeeks:2,progressPercentage:100},'Continue Learning']
 ]) {
  for(const options of [{},{acting:true,preview:true},{acting:true,preview:false}]){
   const ui=await mount({...options,mine:[{...subject,...counts}]});try{
    const link=[...ui.host.querySelectorAll('article a')][0];assert.equal(link.textContent,label);assert.ok(link.getAttribute('href').endsWith('/subjects?subject=15'));
    assert.equal(button(ui.host,'Enroll in Subject'),undefined);
   }finally{await ui.close();}
  }
 }
});
test('long titles remain complete and the card grid reserves a separate badge column',async()=>{
 const title='Hospitality Leadership and Management '.repeat(8);
 const ui=await mount({mine:[{...subject,subjectName:title}]});try{assert.equal(ui.host.querySelector('article h2').textContent,title);assert.match(ui.host.querySelector('article').textContent,/Enrolled/);}finally{await ui.close();}
 const {readFile}=await import('node:fs/promises');const css=await readFile(new URL('../src/pages/student/styles/EnrollmentSubjects.module.css',import.meta.url),'utf8');
 assert.match(css,/grid-template-columns: minmax\(0, 1fr\) auto/);assert.match(css,/min-width: 0; overflow-wrap: anywhere/);assert.match(css,/margin-top: auto/);assert.match(css,/align-items: stretch/);
});


test('subject details retain backend week order, availability, accordion and mode-aware back links',async()=>{
 const weeks=[
  {weekId:8,weekNumber:2,title:'Second lesson',lessonAvailable:true,unlocked:true,completed:true},
  {weekId:7,weekNumber:1,title:'First lesson',lessonAvailable:true,unlocked:true,completed:false},
  {weekId:9,weekNumber:3,title:'Restricted lesson',lessonAvailable:true,unlocked:false},
  {weekId:10,weekNumber:4,title:'Unpublished lesson',lessonAvailable:false,unlocked:true}
 ];
 for(const options of [{},{acting:true,preview:true},{acting:true,preview:false}]){
  const ui=await mount({...options,legacy:true,weeks});try{
   const panel=ui.host.querySelector('#subject-weeks-15');assert.ok(panel);
   const rows=[...panel.querySelectorAll('button')];
   assert.equal(rows.length,4);assert.match(rows[0].textContent,/Week 2.*Second lesson.*Completed.*Review lesson/);
   assert.match(rows[1].textContent,/Week 1.*First lesson.*Available.*Open lesson/);
   assert.ok(rows[2].disabled);assert.ok(rows[3].disabled);assert.match(rows[3].textContent,/Not published/);
   const base=options.acting?`/admin/${options.preview?'student-preview':'student-mode'}/session`:'/student';
   assert.equal(ui.host.querySelector('[aria-label="Learning navigation"] a').getAttribute('href'),base+'/subjects/my');
   const trigger=ui.host.querySelector('button[aria-controls="subject-weeks-15"]');
   await act(async()=>trigger.click());assert.equal(ui.host.querySelector('#subject-weeks-15'),null);
   await act(async()=>trigger.click());assert.ok(ui.host.querySelector('#subject-weeks-15'));
   assert.equal(button(ui.host,'Enroll in Subject'),undefined);
   await act(async()=>ui.host.querySelector('#subject-weeks-15 button').click());
   assert.equal(ui.host.querySelector('[data-testid="path"]').textContent,base+'/lesson/15/8');
  }finally{await ui.close();}
 }
});

test('weekly lesson empty state and responsive layout remain compact and readable',async()=>{
 const ui=await mount({legacy:true});try{assert.match(ui.host.textContent,/No weekly lessons are available yet/);}finally{await ui.close();}
 const {readFile}=await import('node:fs/promises');const css=await readFile(new URL('../src/pages/student/styles/Subjects.module.css',import.meta.url),'utf8');
 assert.match(css,/@media \(max-width: 640px\)/);assert.match(css,/minmax\(0,1fr\)/);assert.match(css,/overflow-wrap: anywhere/);assert.match(css,/:focus-visible/);
});

test('subject views have no year controls and preserve all backend-authorized years in every mode',async()=>{
 const first={...subject,subjectName:'First year learning'};
 const second={...subject,subjectId:16,subjectName:'Second year learning',yearLevel:'SECOND_YEAR'};
 // These are authoritative API fixtures, including cross-year access the client must not hide.
 for(const rows of [[first],[second],[second,first]]){
  for(const mode of [{},{acting:true,preview:true},{acting:true,preview:false}]){
   for(const view of [{mine:rows},{browse:true,available:rows}]){
    const ui=await mount({...mode,...view,mobile:true});try{
     const text=ui.host.querySelector('main').textContent;
     assert.doesNotMatch(text,/All Years|FIRST_YEAR|SECOND_YEAR/);
     assert.equal(ui.host.querySelectorAll('main button[aria-pressed]').length,0);
     for(const row of rows){assert.ok(text.includes(row.subjectName));assert.ok(text.includes(row.yearLevel==='FIRST_YEAR'?'1st Year':'2nd Year'));}
     {
      assert.equal(ui.host.querySelectorAll('article').length,rows.length);
      assert.deepEqual(ui.reads,[view.browse?'browse':'my']);
     }
     if(mode.acting)assert.equal(button(ui.host,'Enroll in Subject'),undefined);
    }finally{await ui.close();}
   }
  }
 }
});

test('Continue Learning opens only the selected enrolled subject and mode-specific weeks, lesson and back routes',async()=>{
 const other={...subject,subjectId:42,subjectCode:'HRM101',subjectName:'Selected hospitality subject'};
 const unrelated={...subject,subjectId:99,subjectName:'Unenrolled compatible subject'};
 for(const mode of [{},{acting:true,preview:true},{acting:true,preview:false}]){
  for(const selected of [subject,other]){
   const ui=await mount({...mode,mine:[subject,other],legacySubjects:[subject,other,unrelated],navigationFlow:true,weeks:[{weekId:71,weekNumber:1,title:'Authorized lesson',lessonAvailable:true,unlocked:true}]});try{
    const link=ui.host.querySelector(`article a[href$="?subject=${selected.subjectId}"]`);assert.ok(link);
    await act(async()=>link.click());
    const main=ui.host.querySelector('main');assert.ok(main.textContent.includes(selected.subjectName));
    for(const row of [subject,other,unrelated].filter(row=>row.subjectId!==selected.subjectId))assert.ok(!main.textContent.includes(row.subjectName));
    assert.equal(main.querySelectorAll('button[aria-expanded]').length,1);
    assert.equal(main.querySelector('[aria-label="Search subjects"]'),null);
    assert.deepEqual(ui.reads,['my',selected.subjectId]);
    const back=main.querySelector('[aria-label="Learning navigation"] a');
    const base=mode.acting?`/admin/${mode.preview?'student-preview':'student-mode'}/session`:'/student';
    assert.equal(back.getAttribute('href'),base+'/subjects/my');
    await act(async()=>back.click());assert.equal(ui.host.querySelectorAll('article').length,2);
   }finally{await ui.close();}
  }
 }
});

test('unknown direct subject IDs never fall back to general subjects or fetch their weeks',async()=>{
 for(const selectedSubject of ['999','', 'not-an-id']){
  const ui=await mount({legacy:true,selectedSubject});try{
   assert.match(ui.host.querySelector('main').textContent,/This subject is not available to you/);
   assert.equal(ui.host.querySelectorAll('main button[aria-expanded]').length,0);assert.deepEqual(ui.reads,[]);
  }finally{await ui.close();}
 }
});

test('selected subjects with no accessible lessons stay in context without opening an arbitrary lesson',async()=>{
 for(const weeks of [[],[{weekId:5,weekNumber:1,title:'Unpublished lesson',lessonAvailable:false,unlocked:false}]]){
  const ui=await mount({legacy:true,weeks,legacySubjects:[subject,{...subject,subjectId:99,subjectName:'Other subject'}]});try{
   assert.match(ui.host.querySelector('main').textContent,/No weekly lessons are available yet|No lessons are currently available to open/);
   assert.ok(!ui.host.querySelector('main').textContent.includes('Other subject'));
   assert.equal(ui.host.querySelector('[data-testid="path"]').textContent,'/student/subjects');
   assert.ok([...ui.host.querySelectorAll('#subject-weeks-15 button')].every(button=>button.disabled));
  }finally{await ui.close();}
 }
});

test('curriculum opens a focused read-only dialog in all modes without enrollment or learning writes',async()=>{
 for(const mode of [{},{acting:true,preview:true},{acting:true,preview:false}]){
  const ui=await mount({...mode,browse:true});try{
   const trigger=button(ui.host,'View Curriculum');assert.ok(trigger);trigger.focus();
   await act(async()=>trigger.click());
   const dialog=document.querySelector('[role="dialog"]');assert.ok(dialog);assert.equal(dialog.getAttribute('aria-modal'),'true');
   assert.equal(dialog.querySelector('h2').textContent,'Curriculum Preview');
   assert.equal(dialog.getAttribute('aria-labelledby'),dialog.querySelector('h2').id);
   assert.match(dialog.textContent,/Published curriculum/);assert.match(dialog.textContent,/Published lesson title/);assert.match(dialog.textContent,/1 Published Week/);
   assert.deepEqual([...dialog.querySelector('[aria-label="Subject metadata"]').children].map(item=>item.textContent),['HRM101','1st Year','1 Published Week']);
   const note=[...dialog.querySelectorAll('p')].find(item=>item.textContent==='Enroll in this subject to access its learning materials and assessments, subject to availability and access requirements.');
   assert.ok(note);assert.ok(dialog.querySelector('ol').compareDocumentPosition(note) & 4);
   assert.equal(dialog.querySelectorAll('a,li button').length,0);assert.equal(dialog.querySelectorAll('button').length,1);
   assert.ok(dialog.contains(document.activeElement));assert.equal(document.body.style.overflow,'hidden');
   await act(async()=>ui.host.click());assert.ok(document.querySelector('[role="dialog"]'));
   assert.deepEqual(ui.reads,['browse','curriculum:15']);
   await act(async()=>button(dialog,'Close').click());assert.equal(document.querySelector('[role="dialog"]'),null);assert.equal(document.activeElement,trigger);
   assert.ok(ui.host.querySelector('article'));assert.equal(button(ui.host,'Enroll in Subject')!==undefined,!mode.acting);
  }finally{await ui.close();}
 }
});

test('curriculum loading aborts on close and recoverable errors retry only the metadata request',async()=>{
 let resolve,signal,calls=0;
 const ui=await mount({browse:true,curriculumLoader:async(id,config)=>{assert.equal(id,15);signal=config.signal;if(++calls===1)throw {response:{status:500}};return new Promise(yes=>{resolve=yes;});}});
 try{
  await act(async()=>button(ui.host,'View Curriculum').click());let dialog=document.querySelector('[role="dialog"]');assert.match(dialog.textContent,/Curriculum could not be loaded/);
  await act(async()=>{button(dialog,'Retry').click();});assert.match(dialog.textContent,/Loading curriculum/);assert.equal(button(dialog,'Retry'),undefined);assert.equal(calls,2);
  await act(async()=>resolve({subjectName:'Correct subject',publishedWeekCount:0,weeks:[]}));assert.match(dialog.textContent,/No published weeks or lessons/);
  await act(async()=>button(dialog,'Close').click());assert.ok(signal.aborted);assert.deepEqual(ui.reads,['browse','curriculum:15','curriculum:15']);
 }finally{await ui.close();}
});

test('curriculum failures use safe specific messages without unrestricted fallback',async()=>{
 for(const [failure,pattern,retry] of [[{response:{status:401}},/session has expired/,false],[{response:{status:403}},/not authorized/,false],[{response:{status:404}},/no longer available/,false],[new Error('private network details'),/Could not reach HALO/,true]]){
  const ui=await mount({browse:true,curriculumLoader:async()=>{throw failure;}});try{
   await act(async()=>button(ui.host,'View Curriculum').click());const dialog=document.querySelector('[role="dialog"]');assert.match(dialog.textContent,pattern);assert.doesNotMatch(dialog.textContent,/private network details/);assert.equal(Boolean(button(dialog,'Retry')),retry);assert.deepEqual(ui.reads,['browse','curriculum:15']);
   await act(async()=>button(dialog,'Close').click());
  }finally{await ui.close();}
 }
});

test('curriculum is subject-specific and closing a pending preview prevents late content from appearing',async()=>{
 let finish,signal;
 const ui=await mount({browse:true,available:[subject,{...subject,subjectId:42,subjectName:'Other eligible subject'}],curriculumLoader:async(id,config)=>{assert.equal(id,42);signal=config.signal;return new Promise(resolve=>{finish=resolve;});}});
 try{
  const trigger=ui.host.querySelectorAll('article')[1].querySelector('button:last-child');trigger.focus();await act(async()=>trigger.click());
  const dialog=document.querySelector('[role="dialog"]');assert.match(dialog.textContent,/Loading curriculum/);
  await act(async()=>dialog.dispatchEvent(new window.KeyboardEvent('keydown',{key:'Tab',bubbles:true,cancelable:true})));assert.ok(dialog.contains(document.activeElement));
  await act(async()=>dialog.dispatchEvent(new window.KeyboardEvent('keydown',{key:'Escape',bubbles:true,cancelable:true})));assert.equal(document.querySelector('[role="dialog"]'),null);assert.ok(signal.aborted);assert.equal(document.activeElement,trigger);
  await act(async()=>finish({subjectName:'Late private result',weeks:[]}));assert.ok(!document.body.textContent.includes('Late private result'));assert.deepEqual(ui.reads,['browse','curriculum:42']);
 }finally{await ui.close();}
});

test('curriculum metadata uses dynamic singular/plural labels and wraps with theme-aware secondary text',async()=>{
 for(const [count,label] of [[0,'0 Published Weeks'],[1,'1 Published Week'],[2,'2 Published Weeks']]){
  const ui=await mount({browse:true,curriculumLoader:async()=>({subjectName:'Actual subject',subjectCode:'HRM002',yearLevel:'SECOND_YEAR',publishedWeekCount:count,weeks:[]})});try{
   await act(async()=>button(ui.host,'View Curriculum').click());const dialog=document.querySelector('[role="dialog"]');
   assert.deepEqual([...dialog.querySelector('[aria-label="Subject metadata"]').children].map(item=>item.textContent),['HRM002','2nd Year',label]);
   assert.match(dialog.textContent,/Preview of published lesson titles\./);assert.match(dialog.textContent,/subject to availability and access requirements\./);
   assert.equal(dialog.querySelectorAll('button').length,1);assert.equal(dialog.querySelectorAll('a').length,0);
   await act(async()=>button(dialog,'Close').click());
  }finally{await ui.close();}
 }
 const {readFile}=await import('node:fs/promises');const css=await readFile(new URL('../src/pages/student/styles/CurriculumPreview.module.css',import.meta.url),'utf8');
 assert.match(css,/\.metadata \{[^}]*display: flex;[^}]*flex-wrap: wrap/);assert.match(css,/content: "\\2022"/);assert.match(css,/color: var\(--color-text-muted\)/);assert.match(css,/@media\(max-width:640px\)/);
});

test('unenrolled legacy subjects never load protected weeks and link to mode-specific Browse Subjects',async()=>{
 for(const mode of [{},{acting:true,preview:true},{acting:true,preview:false}]){
  for(const enrolled of [false,undefined]){
   const ui=await mount({...mode,legacy:true,legacySubjects:[{...subject,enrolled}]});try{
    const main=ui.host.querySelector('main');assert.match(main.textContent,/Enroll in this subject to access learning activities/);assert.deepEqual(ui.reads,[]);
    assert.equal(main.querySelectorAll('#subject-weeks-15 button').length,0);
    const base=mode.acting?`/admin/${mode.preview?'student-preview':'student-mode'}/session`:'/student';
    assert.ok(main.querySelector(`a[href="${base}/subjects/browse"]`));
    assert.ok(!main.textContent.includes('Open lesson'));assert.ok(!main.textContent.includes('Start quiz'));
   }finally{await ui.close();}
  }
 }
});

test('plain Subjects compatibility routes replace the catalog with My Subjects without redirect loops',async()=>{
 for(const mode of [{},{acting:true,preview:true},{acting:true,preview:false}]){
  const base=mode.acting?`/admin/${mode.preview?'student-preview':'student-mode'}/session`:'/student';
  const ui=await mount({...mode,legacy:true,selectedSubject:null,mine:[subject],legacySubjects:[{...subject,subjectName:'Obsolete mixed catalog'}],historyEntries:[base,base+'/subjects']});try{
   assert.equal(ui.host.querySelector('[data-testid="path"]').textContent,base+'/subjects/my');
   assert.equal(ui.host.querySelectorAll('article').length,1);assert.equal(ui.subjectReads,0);assert.deepEqual(ui.reads,['my']);
   assert.ok(!ui.host.textContent.includes('Obsolete mixed catalog'));
   await ui.navigate(-1);assert.equal(ui.host.querySelector('[data-testid="path"]').textContent,base);
   await ui.navigate(1);assert.equal(ui.host.querySelector('[data-testid="path"]').textContent,base+'/subjects/my');
   await act(async()=>ui.host.querySelector('article a').click());assert.equal(ui.host.querySelector('[data-testid="path"]').textContent,base+'/subjects');assert.equal(ui.subjectReads,1);
   await ui.navigate(-1);assert.equal(ui.host.querySelector('[data-testid="path"]').textContent,base+'/subjects/my');
   await ui.navigate(1);assert.equal(ui.host.querySelectorAll('main button[aria-expanded]').length,1);
  }finally{await ui.close();}
 }
});

test('plain Subjects with no enrollment retains the existing My Subjects empty state and Browse route',async()=>{
 for(const mode of [{},{acting:true,preview:true},{acting:true,preview:false}]){
  const ui=await mount({...mode,legacy:true,selectedSubject:null,mine:[]});try{
   assert.match(ui.host.textContent,/You haven't enrolled in any subjects yet/);assert.equal(ui.subjectReads,0);
   assert.equal(ui.host.querySelectorAll('main button[aria-expanded]').length,0);
   assert.equal(ui.host.querySelector('main a[href$="/subjects"]'),null);
   await act(async()=>[...ui.host.querySelectorAll('main a')].find(a=>a.textContent==='Browse Subjects').click());
   assert.ok(ui.host.querySelector('[data-testid="path"]').textContent.endsWith('/subjects/browse'));assert.ok(button(ui.host,'View Curriculum'));
   assert.equal(Boolean(button(ui.host,'Enroll in Subject')),!mode.acting);
  }finally{await ui.close();}
 }
});

test('segmented subject navigation follows routes, keyboard focus and browser history in every mode',async()=>{
 for(const mode of [{},{acting:true,preview:true},{acting:true,preview:false}]){
  const base=mode.acting?`/admin/${mode.preview?'student-preview':'student-mode'}/session`:'/student';
  const ui=await mount({...mode,navigationFlow:true,mine:[subject],mobile:true});try{
   const nav=()=>ui.host.querySelector('nav[aria-label="Subject views"]');
   const assertPage=page=>{
    assert.equal(nav().querySelectorAll('a').length,2);assert.equal(nav().querySelectorAll('[aria-current="page"]').length,1);
    assert.equal(nav().querySelector('[aria-current="page"]').getAttribute('href'),base+'/subjects/'+page);
    assert.deepEqual([...nav().querySelectorAll('a')].map(a=>a.getAttribute('href')),[base+'/subjects/my',base+'/subjects/browse']);
    assert.equal(nav().querySelectorAll('[role="tab"],button').length,0);
   };
   assertPage('my');const browse=nav().querySelector('a[href$="/browse"]');browse.focus();assert.equal(document.activeElement,browse);
   await act(async()=>browse.click());assertPage('browse');assert.ok(button(ui.host,'View Curriculum'));
   assert.equal(Boolean(button(ui.host,'Enroll in Subject')),!mode.acting);
   await ui.navigate(-1);assertPage('my');assert.ok(ui.host.querySelector('article a[href$="?subject=15"]'));
   await ui.navigate(1);assertPage('browse');
   await ui.navigate(base+'/subjects/my?from=bookmark');assertPage('my');
  }finally{await ui.close();}
 }
});

test('segmented navigation uses compact theme tokens, visible focus and wrapping mobile links',async()=>{
 const {readFile}=await import('node:fs/promises');const css=await readFile(new URL('../src/pages/student/styles/EnrollmentSubjects.module.css',import.meta.url),'utf8');
 assert.match(css,/\.links \{[^}]*width: fit-content;[^}]*max-width: 100%/);
 assert.match(css,/\.links a\[aria-current="page"\] \{[^}]*background: var\(--color-primary-soft\);[^}]*border-color: var\(--color-primary-border\);[^}]*font-weight: 600/);
 assert.match(css,/\.links a \{[^}]*text-decoration: none;[^}]*overflow-wrap: anywhere/);
 assert.match(css,/:not\(\[aria-current="page"\]\):hover/);assert.match(css,/:focus-visible/);
 assert.match(css,/@media\(max-width:640px\) \{ \.links \{ width: 100%; \} \.links a \{ flex: 1/);
});
