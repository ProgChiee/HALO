import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';
let dom,server,createRoot,Router,AuthContext,ToastContext,Mode,Selector,Dashboard,Profile,Subjects,api,services,sessionStore;
let width=1440;
const id='350b29ca-a7d5-4a2d-a5d2-903db7e67e77';
const id2='460b29ca-a7d5-4a2d-a5d2-903db7e67e77';
const target={userId:7,name:'Professor A',email:'prof@example.test',role:'PROFESSOR'};
const makeSession=(key=id,who=target)=>({id:key,adminUserId:1,target:who,expiresAt:new Date(Date.now()+1800000).toISOString()});
const response=(config,data,status=200)=>({config,data,status,headers:{}});
const failure=(config,status,code)=>Object.assign(new Error('test failure'),{config,response:{status,data:{code}}});
before(async()=>{
 dom=new JSDOM('<html><body></body></html>',{url:'http://localhost'});
 for(const k of ['window','document','HTMLElement','Event','KeyboardEvent','localStorage','sessionStorage'])globalThis[k]=dom.window[k];
 globalThis.IS_REACT_ACT_ENVIRONMENT=true;
 window.matchMedia=()=>({matches:width<=1024,addEventListener(){},removeEventListener(){}});
 ({createRoot}=await import('react-dom/client'));Router=await import('react-router-dom');
 server=await createServer({server:{middlewareMode:true},appType:'custom'});
 ({AuthContext}=await server.ssrLoadModule('/src/context/login/useAuth.js'));
 ({ToastContext}=await server.ssrLoadModule('/src/context/notifications/useToast.js'));
 const mode=await server.ssrLoadModule('/src/pages/admin/acting/ProfessorMode.jsx');Mode=mode.default;Selector=mode.ProfessorModeSelector;
 Dashboard=(await server.ssrLoadModule('/src/pages/professor/components/ProfessorDashboard.jsx')).default;
 Profile=(await server.ssrLoadModule('/src/pages/professor/components/ProfessorProfile.jsx')).default;
 Subjects=(await server.ssrLoadModule('/src/pages/professor/components/SubjectManagement.jsx')).default;
 api=(await server.ssrLoadModule('/src/services/apiClient.js')).default;
 services=await server.ssrLoadModule('/src/services/admin/professorModeService.js');
 sessionStore=await server.ssrLoadModule('/src/utils/session.js');
});
after(async()=>{await server.close();dom.window.close();});
const click=async el=>act(async()=>el.click());
const button=text=>[...document.querySelectorAll('button')].find(b=>b.textContent===text);
async function mount(path, strict = false) {
 sessionStore.writeSession({id:1,name:'Admin',email:'admin@example.test',role:'ADMIN'},'admin-test-token',false);
 const host=document.createElement('div');document.body.append(host);const root=createRoot(host);
 function Location(){return React.createElement('output',{},Router.useLocation().pathname);}
 await act(async()=>root.render(React.createElement(strict ? React.StrictMode : React.Fragment, {}, React.createElement(AuthContext.Provider,{value:{user:{id:1,name:'Admin'},role:'admin',isAuthenticated:true,logout(){throw Error('Unexpected logout');}}},
 React.createElement(ToastContext.Provider,{value:{showToast(){}}},React.createElement(Router.MemoryRouter,{initialEntries:[path]},
 React.createElement(Router.Routes,{},
 React.createElement(Router.Route,{path:'/admin',element:React.createElement('p',{},'Admin home')}),
 React.createElement(Router.Route,{path:'/admin/professor-mode',element:React.createElement(Selector)}),
 React.createElement(Router.Route,{path:'/admin/professor-mode/:actingSessionId',element:React.createElement(Mode)},
 React.createElement(Router.Route,{index:true,element:React.createElement(Dashboard)}),
 React.createElement(Router.Route,{path:'subjects',element:React.createElement(Subjects)}),
 React.createElement(Router.Route,{path:'profile',element:React.createElement(Profile)}))),React.createElement(Location)))))));
 return async()=>{await act(async()=>root.unmount());host.remove();};
}
function adapter(calls,overrides=()=>undefined){return async config=>{
 calls.push(config);const custom=overrides(config);if(custom!==undefined)return await custom;
 if(config.url==='/admin/acting/targets')return response(config,{content:[target],totalPages:1});
 if(config.method==='post'&&config.url==='/admin/acting/sessions')return response(config,makeSession(),201);
 if(config.url.startsWith('/admin/acting/sessions/'))return response(config,makeSession());
 if(config.url.endsWith('/subjects'))return response(config,[]);
 if(config.url.endsWith('/profile'))return response(config,{...target,status:'ACTIVE',professorId:'P7'});
 return response(config,{totalSubjects:3,totalStudents:10,totalModules:4});
};}
test('selection enters acting routes, keeps Admin login and hides credentials',async()=>{
 const calls=[];api.defaults.adapter=adapter(calls);const cleanup=await mount('/admin/professor-mode');
 try{
  const login=sessionStore.readSession();await click(button('Enter Professor Mode'));
  assert.match(document.body.textContent,/Professor Mode — Acting as Professor A/);
  assert.equal(document.querySelector('output').textContent,'/admin/professor-mode/'+id);
  const create=calls.find(c=>c.method==='post');assert.deepEqual(JSON.parse(create.data),{targetUserId:7,targetRole:'PROFESSOR'});
  await click(document.querySelector('a[href$="/subjects"]'));assert.ok(button('Create Subject'));
  await click(document.querySelector('a[href$="/profile"]'));assert.equal(button('Change password'),undefined);assert.equal(document.querySelector('[role="dialog"]'),null);
  const work=calls.filter(c=>c.url.startsWith('/admin/acting/professor/'));
  assert.ok(work.length>=3);assert.ok(work.every(c=>c.headers.get('X-Acting-Session')===id&&c.headers.get('Authorization')==='Bearer admin-test-token'));
  assert.ok(calls.every(c=>!c.url.startsWith('/professor/')));
  await click(button('Exit Mode'));assert.equal(document.querySelector('output').textContent,'/admin');
  assert.ok(calls.some(c=>c.method==='delete'&&c.url.endsWith(id)));assert.deepEqual(sessionStore.readSession(),login);
 }finally{await cleanup();}
});
test('direct refresh restores session and responsive acting navigation at all widths',async()=>{
 for(width of [1440,768,390]){
  const calls=[];api.defaults.adapter=adapter(calls);const cleanup=await mount('/admin/professor-mode/'+id+'/subjects');
  try{assert.equal(calls[0].url,'/admin/acting/sessions/'+id);assert.match(document.body.textContent,/Acting as Professor A/);
   const toggle=document.querySelector('[aria-label="Open navigation"]');if(width<=1024){assert.ok(toggle);await click(toggle);}
   assert.ok([...document.querySelectorAll('nav a')].every(a=>a.getAttribute('href').startsWith('/admin/professor-mode/'+id)));
   assert.equal(document.querySelector('nav a[aria-current="page"]').getAttribute('href'),'/admin/professor-mode/'+id+'/subjects');
   if(width<=1024){await click(document.querySelector('[aria-label="Close navigation"]'));assert.equal(document.querySelector('[role="dialog"]'),null);}
  }finally{await cleanup();}
 }width=1440;
});
test('wrong, expired and revoked sessions exit without clearing Admin authentication',async()=>{
 for(const kind of ['wrong-role','expired','revoked','runtime']){
  api.defaults.adapter=adapter([],config=>{
   if(config.url==='/admin/acting/sessions/'+id){
    if(kind==='revoked')return Promise.reject(failure(config,409,'ACTING_SESSION_INVALID'));
    if(kind==='wrong-role')return response(config,makeSession(id,{...target,role:'STUDENT'}));
    if(kind==='expired')return response(config,{...makeSession(),expiresAt:'2000-01-01T00:00:00Z'});
   }
   if(kind==='runtime'&&config.url.endsWith('/dashboard'))return Promise.reject(failure(config,409,'ACTING_SESSION_INVALID'));
  });const cleanup=await mount('/admin/professor-mode/'+id);
  try{assert.equal(document.querySelector('output').textContent,'/admin/professor-mode');assert.equal(sessionStore.readSession().token,'admin-test-token');}
  finally{await cleanup();}
 }
});
test('Change Account aborts old requests and clears old page state before new target',async()=>{
 let resolveOld;const calls=[];api.defaults.adapter=adapter(calls,c=>{
  if(c.url.endsWith('/dashboard')&&c.headers.get('X-Acting-Session')===id)return new Promise(resolve=>{resolveOld=()=>resolve(response(c,{totalSubjects:999}));});
  if(c.method==='post'&&c.url==='/admin/acting/sessions')return response(c,makeSession(id2,{...target,name:'Professor B'}));
  if(c.url.endsWith('/sessions/'+id2))return response(c,makeSession(id2,{...target,name:'Professor B'}));
 });const cleanup=await mount('/admin/professor-mode/'+id);
 try{await click(button('Change Account'));assert.equal(document.querySelector('output').textContent,'/admin/professor-mode');
  await click(button('Enter Professor Mode'));await act(async()=>resolveOld());
  assert.match(document.body.textContent,/Acting as Professor B/);assert.doesNotMatch(document.body.textContent,/999/);
  assert.ok(calls.find(c=>c.url.endsWith('/dashboard')&&c.headers.get('X-Acting-Session')===id).signal.aborted);
 }finally{await cleanup();}
});
test('all shared workflow methods route through scoped client while normal defaults stay unchanged',async()=>{
 const calls=[];api.defaults.adapter=adapter(calls);const scope=services.createActingProfessorScope(id,()=>{});
 await scope.api.createSubject({subjectCode:'S',subjectName:'Subject',description:'',yearLevel:'FIRST_YEAR'});
 await scope.api.addWeek(1,{weekNumber:1,title:'Week'});await scope.api.generateLesson(2);await scope.api.approveLesson(2);
 await scope.api.getStudentProgressSummaries();await scope.api.deleteModuleFile(4);
 assert.ok(calls.every(c=>c.url.startsWith('/admin/acting/professor/')&&c.headers.get('X-Acting-Session')===id));
 const normal=await server.ssrLoadModule('/src/services/professor/professorService.js');await normal.getSubjects();
 assert.equal(calls.at(-1).url,'/professor/subjects');assert.equal(calls.at(-1).headers.get('X-Acting-Session'),undefined);
 scope.dispose();await assert.rejects(scope.api.getSubjects(),e=>e.code==='ERR_CANCELED');
});

test('Strict Mode remount keeps Professor requests usable',async()=>{
 const calls=[];api.defaults.adapter=adapter(calls);const cleanup=await mount('/admin/professor-mode/'+id,true);
 try{assert.match(document.body.textContent,/Acting as Professor A/);assert.doesNotMatch(document.body.textContent,/Could not|Couldn.t|Retry/);assert.ok(calls.some(c=>c.url.endsWith('/dashboard')&&!c.signal.aborted));}
 finally{await cleanup();}
});
