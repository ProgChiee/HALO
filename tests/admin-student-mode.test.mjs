import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';
let dom,server,createRoot,Router,AuthContext,ToastContext,Mode,Selector,Dashboard,Profile,Subjects,api,services,sessionStore,Quiz,LessonChat,Progress,Badges;
let width=1440;
const id='350b29ca-a7d5-4a2d-a5d2-903db7e67e77';
const id2='460b29ca-a7d5-4a2d-a5d2-903db7e67e77';
const target={userId:7,name:'Student A',email:'prof@example.test',role:'STUDENT'};
const makeSession=(key=id,who=target)=>({id:key,adminUserId:1,target:who,expiresAt:new Date(Date.now()+1800000).toISOString()});
const response=(config,data,status=200)=>({config,data,status,headers:{}});
const failure=(config,status,code)=>Object.assign(new Error('test failure'),{config,response:{status,data:{code}}});
before(async()=>{
 dom=new JSDOM('<html><body></body></html>',{url:'http://localhost'});
 for(const k of ['window','document','HTMLElement','Event','KeyboardEvent','localStorage','sessionStorage'])globalThis[k]=dom.window[k];
 globalThis.IS_REACT_ACT_ENVIRONMENT=true;
 dom.window.HTMLElement.prototype.scrollIntoView=()=>{};
 window.matchMedia=()=>({matches:width<=1024,addEventListener(){},removeEventListener(){}});
 ({createRoot}=await import('react-dom/client'));Router=await import('react-router-dom');
 server=await createServer({server:{middlewareMode:true},appType:'custom'});
 ({AuthContext}=await server.ssrLoadModule('/src/context/login/useAuth.js'));
 ({ToastContext}=await server.ssrLoadModule('/src/context/notifications/useToast.js'));
 const mode=await server.ssrLoadModule('/src/pages/admin/acting/StudentMode.jsx');Mode=mode.default;Selector=mode.StudentModeSelector;
 Dashboard=(await server.ssrLoadModule('/src/pages/student/components/StudentDashboard.jsx')).default;
 Profile=(await server.ssrLoadModule('/src/pages/student/components/Profile.jsx')).default;
 Subjects=(await server.ssrLoadModule('/src/pages/student/components/Subjects.jsx')).default;
 Quiz=(await server.ssrLoadModule('/src/pages/student/components/Quiz.jsx')).default;
 LessonChat=(await server.ssrLoadModule('/src/pages/student/components/LessonChat.jsx')).default;
 Progress=(await server.ssrLoadModule('/src/pages/student/components/Progress.jsx')).default;
 Badges=(await server.ssrLoadModule('/src/pages/student/components/Badges.jsx')).default;
 api=(await server.ssrLoadModule('/src/services/apiClient.js')).default;
 services=await server.ssrLoadModule('/src/services/admin/studentModeService.js');
 sessionStore=await server.ssrLoadModule('/src/utils/session.js');
});
after(async()=>{await server.close();dom.window.close();});
const click=async el=>act(async()=>el.click());
async function enterSupport(){await click(button('Select Student'));await click(button('Enter Support Mode'));await click(button('Confirm Support Mode'));}
const button=text=>[...document.querySelectorAll('button')].find(b=>b.textContent===text);
async function mount(path, strict = false) {
 sessionStore.writeSession({id:1,name:'Admin',email:'admin@example.test',role:'ADMIN'},'admin-test-token',false);
 const host=document.createElement('div');document.body.append(host);const root=createRoot(host);
 function Location(){return React.createElement('output',{},Router.useLocation().pathname);}
 const modeRoutes=(path,preview)=>React.createElement(Router.Route,{path,element:React.createElement(Mode,{preview})},
  ...[[undefined,Dashboard],['subjects',Subjects],['lesson/:topicId/:weekId',LessonChat],['quiz/:topicId/:weekId',Quiz],['progress',Progress],['badges',Badges],['profile',Profile]].map(([path,Component])=>React.createElement(Router.Route,{key:path||'index',path,index:path===undefined,element:React.createElement(Component)})));
 await act(async()=>root.render(React.createElement(strict ? React.StrictMode : React.Fragment, {}, React.createElement(AuthContext.Provider,{value:{user:{id:1,name:'Admin'},role:'admin',isAuthenticated:true,logout(){throw Error('Unexpected logout');}}},
 React.createElement(ToastContext.Provider,{value:{showToast(){}}},React.createElement(Router.MemoryRouter,{initialEntries:[path]},
 React.createElement(Router.Routes,{},
 React.createElement(Router.Route,{path:'/admin',element:React.createElement('p',{},'Admin home')}),
 React.createElement(Router.Route,{path:'/admin/student-mode',element:React.createElement(Selector)}),
 modeRoutes('/admin/student-mode/:actingSessionId',false),modeRoutes('/admin/student-preview/:actingSessionId',true)),React.createElement(Location)))))));
 return async()=>{await act(async()=>root.unmount());host.remove();};
}
function adapter(calls,overrides=()=>undefined){return async config=>{
 calls.push(config);const custom=overrides(config);if(custom!==undefined)return await custom;
 if(config.url==='/admin/acting/targets')return response(config,{content:[target],totalPages:1});
 if(config.method==='post'&&config.url==='/admin/acting/sessions')return response(config,makeSession(),201);
 if(config.url.startsWith('/admin/acting/sessions/'))return response(config,makeSession());
 if(config.method==='post'&&config.url==='/admin/preview/student/sessions')return response(config,makeSession());
 if(config.url.startsWith('/admin/preview/student/sessions/'))return response(config,makeSession());
 if(config.url.endsWith('/badges'))return response(config,[]);
 if(config.url.includes('/ai-learning-modules/week/'))return response(config,{id:10,subjectId:1,weekId:37,weekNumber:1,status:'APPROVED',aiGenerationStatus:'COMPLETED',generatedKnowledge:'Lesson knowledge'});
 if(config.url.includes('/assessment/status/'))return response(config,{assessmentExists:true,assessmentAvailable:true,canTakeAssessment:true});
 if(config.url.endsWith('/assessment/10'))return response(config,{id:4,title:'Assessment',passingScore:70,questions:[{id:1,questionText:'Question?',optionA:'Answer',optionB:'Other'}]});
 if(config.url.includes('/assessment/start/'))return response(config,{attemptId:5,assessmentId:4});
 if(config.url.includes('/assessment/result/'))return response(config,{attemptId:5,score:100,passed:true,feedback:[]});
 if(config.url.includes('/mentor/open/'))return response(config,{sessionId:8,moduleId:10,messages:[{id:30,sender:'HALO',message:'Recent message'}],hasOlder:true,nextBeforeId:30});
 if(config.url.endsWith('/subjects'))return response(config,[]);
 if(config.url.endsWith('/profile'))return response(config,{...target,status:'ACTIVE',studentId:'P7'});
 return response(config,{completedModules:3,passedAssessments:2,totalBadges:4});
};}
test('selection enters acting routes, keeps Admin login and hides credentials',async()=>{
 const calls=[];api.defaults.adapter=adapter(calls);const cleanup=await mount('/admin/student-mode');
 try{
  const login=sessionStore.readSession();await enterSupport();
  assert.match(document.body.textContent,/SUPPORT MODE .* Acting as: Student A/);
  assert.equal(document.querySelector('output').textContent,'/admin/student-mode/'+id);
  const create=calls.find(c=>c.method==='post');assert.deepEqual(JSON.parse(create.data),{targetUserId:7,targetRole:'STUDENT'});
  await click(document.querySelector('a[href$="/subjects"]'));assert.match(document.body.textContent,/Subjects/);
  await click(document.querySelector('a[href$="/profile"]'));assert.equal(button('Change password'),undefined);assert.equal(document.querySelector('[role="dialog"]'),null);
  const work=calls.filter(c=>c.url.startsWith('/admin/acting/student/'));
  assert.ok(work.length>=3);assert.ok(work.every(c=>c.headers.get('X-Acting-Session')===id&&c.headers.get('Authorization')==='Bearer admin-test-token'));
  assert.ok(calls.every(c=>!c.url.startsWith('/student/')));
  await click(button('Exit Mode'));assert.equal(document.querySelector('output').textContent,'/admin');
  assert.ok(calls.some(c=>c.method==='delete'&&c.url.endsWith(id)));assert.deepEqual(sessionStore.readSession(),login);
 }finally{await cleanup();}
});
test('direct refresh restores session and responsive acting navigation at all widths',async()=>{
 for(width of [1440,768,390]){
  const calls=[];api.defaults.adapter=adapter(calls);const cleanup=await mount('/admin/student-mode/'+id+'/subjects');
  try{assert.equal(calls[0].url,'/admin/acting/sessions/'+id);assert.match(document.body.textContent,/Acting as: Student A/);
   const toggle=document.querySelector('[aria-label="Open navigation"]');if(width<=1024){assert.ok(toggle);await click(toggle);}
   assert.ok([...document.querySelectorAll('nav a')].every(a=>a.getAttribute('href').startsWith('/admin/student-mode/'+id)));
   assert.equal(document.querySelector('nav a[aria-current="page"]').getAttribute('href'),'/admin/student-mode/'+id+'/subjects');
   if(width<=1024){await click(document.querySelector('[aria-label="Close navigation"]'));assert.equal(document.querySelector('[role="dialog"]'),null);}
  }finally{await cleanup();}
 }width=1440;
});
test('wrong, expired and revoked sessions exit without clearing Admin authentication',async()=>{
 for(const kind of ['wrong-role','expired','revoked','runtime']){
  api.defaults.adapter=adapter([],config=>{
   if(config.url==='/admin/acting/sessions/'+id){
    if(kind==='revoked')return Promise.reject(failure(config,409,'ACTING_SESSION_INVALID'));
    if(kind==='wrong-role')return response(config,makeSession(id,{...target,role:'PROFESSOR'}));
    if(kind==='expired')return response(config,{...makeSession(),expiresAt:'2000-01-01T00:00:00Z'});
   }
   if(kind==='runtime'&&config.url.endsWith('/dashboard'))return Promise.reject(failure(config,409,'ACTING_SESSION_INVALID'));
  });const cleanup=await mount('/admin/student-mode/'+id);
  try{assert.equal(document.querySelector('output').textContent,'/admin/student-mode');assert.equal(sessionStore.readSession().token,'admin-test-token');}
  finally{await cleanup();}
 }
});
test('Change Account aborts old requests and clears old page state before new target',async()=>{
 let resolveOld;const calls=[];api.defaults.adapter=adapter(calls,c=>{
  if(c.url.endsWith('/dashboard')&&c.headers.get('X-Acting-Session')===id)return new Promise(resolve=>{resolveOld=()=>resolve(response(c,{completedModules:999}));});
  if(c.url==='/admin/acting/targets')return response(c,{content:[{...target,userId:8,name:'Student B'}],totalPages:1});
  if(c.method==='post'&&c.url==='/admin/acting/sessions')return response(c,makeSession(id2,{...target,userId:8,name:'Student B'}));
  if(c.url.endsWith('/sessions/'+id2))return response(c,makeSession(id2,{...target,userId:8,name:'Student B'}));
 });const cleanup=await mount('/admin/student-mode/'+id);
 try{await click(button('Change Account'));assert.equal(document.querySelector('output').textContent,'/admin/student-mode');
  await enterSupport();await act(async()=>resolveOld());
  assert.match(document.body.textContent,/Acting as: Student B/);assert.doesNotMatch(document.body.textContent,/999/);
  assert.ok(calls.find(c=>c.url.endsWith('/dashboard')&&c.headers.get('X-Acting-Session')===id).signal.aborted);
 }finally{await cleanup();}
});
test('all shared workflow methods route through scoped client while normal defaults stay unchanged',async()=>{
 const calls=[];api.defaults.adapter=adapter(calls);const scope=services.createActingStudentScope(id,()=>{});
 await scope.api.getDashboardData();await scope.api.getSubjectsData();await scope.api.getSubjectWeeks(1);await scope.api.getWeekLesson(2);
 await scope.api.getProgressData();await scope.api.getBadgesData();await scope.api.getProfileData();
 await scope.api.getAssessmentStatus(2);await scope.api.getAssessment(2);await scope.api.startAttempt(2);await scope.api.submitAttempt(3,[{questionId:1,answer:'A'}]);await scope.api.getAttemptResult(3);await scope.api.getAttemptHistory(2);
 await scope.api.openSession(2);await scope.api.sendMessage(8,'Question',{requestId:id2});await scope.api.getExchange(8,id2);await scope.api.getConversation(8,20);
 assert.ok(calls.every(c=>c.url.startsWith('/admin/acting/student/')&&c.headers.get('X-Acting-Session')===id));
 const normal=await server.ssrLoadModule('/src/services/student/studentService.js');await normal.getSubjectsData();
 assert.equal(calls.at(-1).url,'/student/subjects');assert.equal(calls.at(-1).headers.get('X-Acting-Session'),undefined);
 scope.dispose();await assert.rejects(scope.api.getSubjectsData(),e=>e.code==='ERR_CANCELED');
});

const inputText=async (input,value)=>act(async()=>{
 Object.getOwnPropertyDescriptor(dom.window.HTMLInputElement.prototype,'value').set.call(input,value);
 input.dispatchEvent(new dom.window.Event('input',{bubbles:true}));
});
test('acting quiz recovers a committed result without resubmitting and retains scoped headers',async()=>{
 const calls=[];api.defaults.adapter=adapter(calls,c=>{
  if(c.url.includes('/assessment/submit/'))return Promise.reject(Object.assign(new Error('timeout'),{code:'ECONNABORTED',config:c}));
 });
 const cleanup=await mount('/admin/student-mode/'+id+'/quiz/1/37');
 try{
  assert.match(document.body.textContent,/Week 1/);await click(button('Start quiz'));
  await click(document.querySelector('[aria-pressed="false"]'));await click(button('Submit quiz'));
  assert.match(document.body.textContent,/100%/);
  assert.equal(calls.filter(c=>c.url.includes('/assessment/submit/')).length,1);
  assert.ok(calls.some(c=>c.url.includes('/assessment/result/5')));
  assert.ok(calls.filter(c=>c.url.includes('/assessment/')).every(c=>c.headers.get('X-Acting-Session')===id));
 }finally{await cleanup();}
});
test('acting Mentor loads older messages and reconciles a lost POST with the same request key',async()=>{
 const calls=[];let sentKey;api.defaults.adapter=adapter(calls,c=>{
  if(c.url.includes('/mentor/session/'))return response(c,{sessionId:8,moduleId:10,messages:[{id:1,sender:'HALO',message:'Older message'},{id:30,sender:'HALO',message:'Recent message'}],hasOlder:false});
  if(c.method==='post'&&c.url.includes('/mentor/message/')){sentKey=JSON.parse(c.data).requestId;return Promise.reject(Object.assign(new Error('timeout'),{config:c,code:'ECONNABORTED'}));}
  if(c.url.includes('/request/')){assert.ok(c.url.endsWith(sentKey));return response(c,{sessionId:8,moduleId:10,haloMessage:'Saved answer. Module sources: lesson.pdf (page 2)'});}
 });
 const cleanup=await mount('/admin/student-mode/'+id+'/lesson/1/37');
 try{
  await click(button('Load older messages'));assert.match(document.body.textContent,/Older message/);
  assert.equal(document.body.textContent.split('Recent message').length-1,1);
  await inputText(document.querySelector('[aria-label="Message AI Mentor"]'),'Explain this lesson');
  await click(document.querySelector('[aria-label="Send message"]'));
  assert.ok(document.body.textContent.includes("lesson.pdf (page 2)"));assert.ok(sentKey);
  assert.equal(calls.filter(c=>c.method==='post'&&c.url.includes('/mentor/message/')).length,1);
  assert.ok(calls.filter(c=>c.url.includes('/mentor/')).every(c=>c.headers.get('X-Acting-Session')===id));
 }finally{await cleanup();}
});
test('switching accounts discards delayed lesson, quiz recovery and Mentor replies',async()=>{
 for(const scenario of ['lesson','quiz','mentor']){
  let finish;const calls=[];api.defaults.adapter=adapter(calls,c=>{
   const pending=scenario==='lesson'?c.url.includes('/ai-learning-modules/week/'):scenario==='quiz'?c.url.includes('/assessment/result/'):c.method==='post'&&c.url.includes('/mentor/message/');
   if(pending)return new Promise(resolve=>{finish=()=>resolve(response(c,scenario==='lesson'?{id:10,subjectId:1,weekId:37,weekNumber:1,status:'APPROVED',aiGenerationStatus:'COMPLETED',generatedKnowledge:'STALE_PRIVATE'}:scenario==='quiz'?{attemptId:5,score:100,passed:true}:{sessionId:8,moduleId:10,haloMessage:'STALE_PRIVATE'}));});
   if(c.url.includes('/assessment/submit/'))return Promise.reject(Object.assign(new Error('timeout'),{config:c,code:'ECONNABORTED'}));
   if(c.url==='/admin/acting/targets')return response(c,{content:[{...target,userId:8,name:'Student B'}],totalPages:1});
  if(c.method==='post'&&c.url==='/admin/acting/sessions')return response(c,makeSession(id2,{...target,userId:8,name:'Student B'}));
   if(c.url.endsWith('/sessions/'+id2))return response(c,makeSession(id2,{...target,userId:8,name:'Student B'}));
  });
  const cleanup=await mount('/admin/student-mode/'+id+(scenario==='quiz'?'/quiz/1/37':'/lesson/1/37'));
  try{
   if(scenario==='quiz'){await click(button('Start quiz'));await click(document.querySelector('[aria-pressed="false"]'));await click(button('Submit quiz'));}
   if(scenario==='mentor'){await inputText(document.querySelector('[aria-label="Message AI Mentor"]'),'Question');await click(document.querySelector('[aria-label="Send message"]'));}
   assert.ok(finish);await click(button('Change Account'));await enterSupport();await act(async()=>finish());
   assert.match(document.body.textContent,/Acting as: Student B/);assert.doesNotMatch(document.body.textContent,/STALE_PRIVATE|100%/);
   assert.ok(calls.filter(c=>c.url.startsWith('/admin/acting/student/')&&c.headers.get('X-Acting-Session')===id).some(c=>c.signal.aborted));
  }finally{await cleanup();}
 }
});
test('Progress and Badges reuse acting APIs and eligibility errors keep a valid session',async()=>{
 for(const path of ['progress','badges']){
  const calls=[];api.defaults.adapter=adapter(calls);const cleanup=await mount('/admin/student-mode/'+id+'/'+path);
  try{assert.match(document.body.textContent,/Acting as: Student A/);assert.ok(calls.some(c=>c.url==='/admin/acting/student/'+(path==='progress'?'subjects':'badges')));}
  finally{await cleanup();}
 }
 let exited=false;api.defaults.adapter=adapter([],c=>c.url.endsWith('/ai-learning-modules/week/37')?Promise.reject(failure(c,403,'STUDENT_NOT_ENROLLED')):undefined);
 const scope=services.createActingStudentScope(id,()=>{exited=true;});
 await assert.rejects(scope.api.getWeekLesson(37));assert.equal(exited,false);scope.dispose();
});

test('Strict Mode remount restores usable acting requests',async()=>{
 const calls=[];api.defaults.adapter=adapter(calls);
 const cleanup=await mount('/admin/student-mode/'+id+'/lesson/1/37',true);
 try{assert.match(document.body.textContent,/Lesson knowledge/);assert.equal(document.querySelector('[aria-label="Message AI Mentor"]').disabled,false);}
 finally{await cleanup();}
});


test('Preview is recommended and creates a separate non-persistent session; Support requires confirmation',async()=>{
 const calls=[];api.defaults.adapter=adapter(calls);const cleanup=await mount('/admin/student-mode');
 try{
  await click(button('Select Student'));assert.match(document.body.textContent,/Recommended/);assert.equal(calls.filter(c=>c.method==='post').length,0);
  await click(button('Enter Support Mode'));assert.match(document.querySelector('[role="dialog"]').textContent,/real quiz attempts, progress, completion, badges, Mentor history/);
  assert.equal(calls.filter(c=>c.method==='post').length,0);await click(button('Cancel'));
  await click(button('Preview as Student'));assert.equal(document.querySelector('output').textContent,'/admin/student-preview/'+id);
  assert.match(document.body.textContent,/PREVIEW MODE/);assert.match(document.body.textContent,/No changes will be saved/);
  const create=calls.find(c=>c.method==='post');assert.equal(create.url,'/admin/preview/student/sessions');assert.deepEqual(JSON.parse(create.data),{targetUserId:7});
  await click(document.querySelector('a[href$="/profile"]'));assert.equal(button('Change password'),undefined);
  assert.ok(calls.filter(c=>c.url.includes('/student/')).every(c=>c.url.startsWith('/admin/preview/student/')));
  await click(button('Exit Mode'));assert.equal(document.querySelector('output').textContent,'/admin');assert.equal(sessionStore.readSession().token,'admin-test-token');
  assert.ok(calls.some(c=>c.method==='delete'&&c.url==='/admin/preview/student/sessions/'+id));
 }finally{await cleanup();}
});
test('Preview refresh restores read-only context and every Student workflow uses the preview namespace',async()=>{
 const calls=[];api.defaults.adapter=adapter(calls);const cleanup=await mount('/admin/student-preview/'+id+'/subjects');
 try{assert.equal(calls[0].url,'/admin/preview/student/sessions/'+id);assert.match(document.body.textContent,/Viewing as: Student A/);}
 finally{await cleanup();}
 calls.length=0;const scope=services.createActingStudentScope(id,()=>{},true);
 try{
  await scope.api.getDashboardData();await scope.api.getSubjectsData();await scope.api.getSubjectWeeks(1);await scope.api.getWeekLesson(37);await scope.api.getProgressData();await scope.api.getBadgesData();await scope.api.getProfileData();
  await scope.api.getAssessment(10);await scope.api.getAssessmentStatus(10);await scope.api.startAttempt(10);await scope.api.submitAttempt(5,[{questionId:1,answer:'A'}]);await scope.api.getAttemptResult(5);await scope.api.getAttemptHistory(10);
  await scope.api.openSession(10);await scope.api.sendMessage(8,'Question',{requestId:id2});await scope.api.getExchange(8,id2);await scope.api.getConversation(8,20);
  assert.ok(calls.every(c=>c.url.startsWith('/admin/preview/student/')&&c.headers.get('X-Acting-Session')===id));
 }finally{scope.dispose();}
});
test('Preview quiz returns a labeled temporary result and reconciles lost responses without real POSTs',async()=>{
 for(const lost of [false,true]){
  const calls=[];api.defaults.adapter=adapter(calls,c=>{
   if(c.url.includes('/assessment/submit/'))return lost?Promise.reject(Object.assign(new Error('timeout'),{code:'ECONNABORTED',config:c})):response(c,{attemptId:5,score:100,passed:true,preview:true,correctCount:1,totalQuestions:1});
  });const cleanup=await mount('/admin/student-preview/'+id+'/quiz/1/37');
  try{
   await click(button('Start quiz'));await click(document.querySelector('[aria-pressed="false"]'));await click(button('Submit quiz'));
   assert.match(document.body.textContent,/Preview Score: 100%/);assert.match(document.body.textContent,/No Student progress or academic records were changed/);
   assert.equal(calls.filter(c=>c.url.includes('/assessment/submit/')).length,1);assert.ok(calls.every(c=>!c.url.startsWith('/admin/acting/student/')&&!c.url.startsWith('/student/')));
  }finally{await cleanup();}
 }
});
test('Preview Mentor retains pagination, citations and request-key reconciliation in the preview API',async()=>{
 const calls=[];let key;api.defaults.adapter=adapter(calls,c=>{
  if(c.url.includes('/mentor/session/'))return response(c,{sessionId:8,moduleId:10,messages:[{id:1,sender:'HALO',message:'Older temporary message'}],hasOlder:false});
  if(c.method==='post'&&c.url.includes('/mentor/message/')){key=JSON.parse(c.data).requestId;return Promise.reject(Object.assign(new Error('timeout'),{config:c,code:'ECONNABORTED'}));}
  if(c.url.includes('/request/')){assert.ok(c.url.endsWith(key));return response(c,{sessionId:8,moduleId:10,haloMessage:'Temporary reply. Module sources: lesson.pdf (page 2)'});}
 });const cleanup=await mount('/admin/student-preview/'+id+'/lesson/1/37');
 try{
  assert.match(document.body.textContent,/Preview conversation/);assert.match(document.body.textContent,/will not be added/);
  await click(button('Load older messages'));assert.match(document.body.textContent,/Older temporary message/);
  await inputText(document.querySelector('[aria-label="Message AI Mentor"]'),'Question');await click(document.querySelector('[aria-label="Send message"]'));
  assert.ok(document.body.textContent.includes('lesson.pdf (page 2)'));assert.ok(key);assert.equal(calls.filter(c=>c.method==='post'&&c.url.includes('/mentor/message/')).length,1);
  assert.ok(calls.every(c=>c.url.startsWith('/admin/preview/student/')));
 }finally{await cleanup();}
});
test('Preview switching aborts old lesson, quiz and Mentor requests and clears temporary state',async()=>{
 for(const scenario of ['lesson','quiz','mentor']){
  let finish;const calls=[];api.defaults.adapter=adapter(calls,c=>{
   const pending=scenario==='lesson'?c.url.includes('/ai-learning-modules/week/'):scenario==='quiz'?c.url.includes('/assessment/result/'):c.method==='post'&&c.url.includes('/mentor/message/');
   if(pending)return new Promise(resolve=>{finish=()=>resolve(response(c,scenario==='lesson'?{id:10,subjectId:1,weekId:37,weekNumber:1,status:'APPROVED',aiGenerationStatus:'COMPLETED',generatedKnowledge:'STALE_PRIVATE'}:scenario==='quiz'?{attemptId:5,score:100,passed:true}:{sessionId:8,moduleId:10,haloMessage:'STALE_PRIVATE'}));});
   if(c.url.includes('/assessment/submit/'))return Promise.reject(Object.assign(new Error('timeout'),{config:c,code:'ECONNABORTED'}));
   if(c.url==='/admin/acting/targets')return response(c,{content:[{...target,userId:8,name:'Student B'}],totalPages:1});
   if(c.method==='post'&&c.url==='/admin/preview/student/sessions')return response(c,makeSession(id2,{...target,userId:8,name:'Student B'}));
   if(c.url.endsWith('/sessions/'+id2))return response(c,makeSession(id2,{...target,userId:8,name:'Student B'}));
  });const cleanup=await mount('/admin/student-preview/'+id+(scenario==='quiz'?'/quiz/1/37':'/lesson/1/37'));
  try{
   if(scenario==='quiz'){await click(button('Start quiz'));await click(document.querySelector('[aria-pressed="false"]'));await click(button('Submit quiz'));}
   if(scenario==='mentor'){await inputText(document.querySelector('[aria-label="Message AI Mentor"]'),'Question');await click(document.querySelector('[aria-label="Send message"]'));}
   assert.ok(finish);await click(button('Change Account'));await click(button('Select Student'));await click(button('Preview as Student'));await act(async()=>finish());
   assert.match(document.body.textContent,/Viewing as: Student B/);assert.doesNotMatch(document.body.textContent,/STALE_PRIVATE|100%/);
   assert.ok(calls.filter(c=>c.url.startsWith('/admin/preview/student/')&&c.headers.get('X-Acting-Session')===id).some(c=>c.signal.aborted));
  }finally{await cleanup();}
 }
});
test('Preview wrong-role, expired, revoked and runtime-invalid sessions return to Admin selection without logout',async()=>{
 for(const kind of ['wrong-role','expired','revoked','runtime']){
  api.defaults.adapter=adapter([],c=>{
   if(c.url==='/admin/preview/student/sessions/'+id){
    if(kind==='revoked')return Promise.reject(failure(c,409,'ACTING_SESSION_INVALID'));
    if(kind==='wrong-role')return response(c,makeSession(id,{...target,role:'PROFESSOR'}));
    if(kind==='expired')return response(c,{...makeSession(),expiresAt:'2000-01-01T00:00:00Z'});
   }
   if(kind==='runtime'&&c.url.endsWith('/dashboard'))return Promise.reject(failure(c,409,'ACTING_SESSION_INVALID'));
  });const cleanup=await mount('/admin/student-preview/'+id);
  try{assert.equal(document.querySelector('output').textContent,'/admin/student-mode');assert.equal(sessionStore.readSession().token,'admin-test-token');}finally{await cleanup();}
 }
});

test('lesson study tracking uses Support writes but never Preview writes',async()=>{
 for(const preview of [false,true]){
  const calls=[];api.defaults.adapter=adapter(calls);const cleanup=await mount('/admin/'+(preview?'student-preview/':'student-mode/')+id+'/lesson/1/37');
  try{
   const writes=calls.filter(c=>c.method==='post'&&c.url.endsWith('/study'));
   assert.equal(writes.length,preview?0:1);
   if(!preview){assert.equal(writes[0].url,'/admin/acting/student/ai-learning-modules/week/37/study');assert.equal(writes[0].headers.get('X-Acting-Session'),id);assert.ok(writes[0].signal);}
  }finally{await cleanup();}
 }
});
