import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';
let dom,server,createRoot,Router,Badges,AuthContext,Workflow,api,createScope,createService;
const definitions=[{badgeType:'TEST_A',badgeName:'First milestone',description:'Complete a practice lesson.'},{badgeType:'TEST_B',badgeName:'Next milestone',description:'Pass a practice assessment.'},{badgeType:'TEST_C',badgeName:'Final milestone',description:'Complete the practice series.'}];
before(async()=>{
 dom=new JSDOM('<html><body></body></html>',{url:'http://localhost'});
 for(const key of ['window','document','HTMLElement','Event','localStorage','sessionStorage'])globalThis[key]=dom.window[key];
 globalThis.IS_REACT_ACT_ENVIRONMENT=true;
 ({createRoot}=await import('react-dom/client'));Router=await import('react-router-dom');
 server=await createServer({server:{middlewareMode:true},appType:'custom'});
 Badges=(await server.ssrLoadModule('/src/pages/student/components/Badges.jsx')).default;
 ({AuthContext}=await server.ssrLoadModule('/src/context/login/useAuth.js'));
 ({StudentWorkflowContext:Workflow}=await server.ssrLoadModule('/src/context/student/useStudentWorkflow.js'));
 api=(await server.ssrLoadModule('/src/services/apiClient.js')).default;
 ({createActingStudentScope:createScope}=await server.ssrLoadModule('/src/services/admin/studentModeService.js'));
 ({createStudentService:createService}=await server.ssrLoadModule('/src/services/student/studentService.js'));
});
after(async()=>{await server?.close();dom?.window.close();});
async function mount(api, mode = {}){
 const host=document.createElement('div');document.body.append(host);const root=createRoot(host);
 await act(async()=>root.render(React.createElement(Router.MemoryRouter,null,React.createElement(AuthContext.Provider,{value:{user:{name:'Student'},role:'student',logout(){}}},React.createElement(Workflow.Provider,{value:{api,basePath:'/student',acting:false,target:{name:'Synthetic Student'},leave(){},...mode}},React.createElement(Badges))))));
 return {host,async close(){await act(async()=>root.unmount());host.remove();}};
}
const section=(host,title)=>[...host.querySelectorAll('h2')].find(h=>h.textContent===title).closest('section');
test('earned and all locked cards use backend data, retain historical text and match counts',async()=>{
 const earned=[{id:1,badgeType:'TEST_A',badgeName:'Historical earned title',description:'Historical earned description'}];
 const view=await mount({getBadgesData:async()=>earned,getBadgeCatalog:async()=>definitions});
 try{
  const won=section(view.host,'Earned'),locked=section(view.host,'Badges to Unlock');
  assert.equal(won.querySelectorAll('li').length,1);assert.equal(locked.querySelectorAll('li').length,2);
  assert.match(won.textContent,/Historical earned title/);assert.match(won.textContent,/Historical earned description/);
  assert.equal(won.querySelectorAll('svg').length,1);assert.equal(locked.querySelectorAll('svg').length,2);
  assert.match(view.host.querySelector('header').textContent,/1 earned/);assert.match(locked.textContent,/2 remaining/);
  assert.doesNotMatch(locked.textContent,/First milestone/);assert.match(locked.textContent,/Next milestone/);assert.match(locked.textContent,/Pass a practice assessment/);
  assert.equal([...view.host.querySelectorAll('li span')].filter(s=>s.textContent==='Earned').length,1);
  assert.equal([...view.host.querySelectorAll('li span')].filter(s=>s.textContent==='Locked').length,2);
 }finally{await view.close();}
});
test('zero earned displays every catalog badge; all earned has a positive unlocked state',async()=>{
 for(const earned of [[],definitions.map((d,i)=>({...d,id:i+1}))]){
  const view=await mount({getBadgesData:async()=>earned,getBadgeCatalog:async()=>definitions});try{
   assert.equal(view.host.querySelectorAll('li h3').length,definitions.length);
   const locked=section(view.host,'Badges to Unlock');
   if(!earned.length){assert.match(view.host.textContent,/No badges earned yet/);assert.equal(locked.querySelectorAll('li').length,3);}
   else {assert.equal(locked.querySelectorAll('li').length,0);assert.match(locked.textContent,/earned every available badge/);}
  }finally{await view.close();}
 }
});
test('catalog loading and failure retain earned cards; retry retrieves catalog without award mutations',async()=>{
 let resolve,fail,catalogCalls=0,earnedCalls=0;
 const view=await mount({getBadgesData:async()=>{earnedCalls++;return [{...definitions[0],id:1}];},getBadgeCatalog:()=>{catalogCalls++;return new Promise((yes,no)=>{resolve=yes;fail=no;});}});
 try{
  assert.match(view.host.textContent,/Loading badges to unlock/);assert.equal(section(view.host,'Earned').querySelectorAll('li').length,1);
  await act(async()=>fail({response:{status:500}}));assert.ok(view.host.querySelector('[role=alert]'));assert.equal(section(view.host,'Earned').querySelectorAll('li').length,1);
  await act(async()=>[...view.host.querySelectorAll('button')].find(b=>b.textContent==='Retry badge catalog').click());
  assert.match(view.host.textContent,/Loading badges to unlock/);await act(async()=>resolve(definitions));
  assert.equal(catalogCalls,2);assert.equal(earnedCalls,1);assert.equal(view.host.querySelector('[role=alert]'),null);assert.equal(section(view.host,'Badges to Unlock').querySelectorAll('li').length,2);
 }finally{await view.close();}
});
test('earned failure never mislabels unknown awards as locked and provides a retry',async()=>{
 let fail=true;const view=await mount({getBadgesData:async()=>{if(fail)throw {response:{status:403}};return [];},getBadgeCatalog:async()=>definitions});try{
  assert.ok(view.host.querySelector('[role=alert]'));assert.equal(view.host.querySelectorAll('li h3').length,0);
  fail=false;await act(async()=>[...view.host.querySelectorAll('button')].find(b=>b.textContent==='Retry badges').click());
  assert.equal(section(view.host,'Badges to Unlock').querySelectorAll('li').length,3);
 }finally{await view.close();}
});
test('normal, Preview and Support catalog requests stay GET-only in their authorized scopes',async()=>{
 const adapter=api.defaults.adapter,calls=[];api.defaults.adapter=async config=>{calls.push(config);return {status:200,headers:{},config,data:[]};};
 try{
  await createService().getBadgeCatalog();
  for(const preview of [true,false]){const scope=createScope('synthetic-session',()=>{},preview);try{await scope.api.getBadgeCatalog();}finally{scope.dispose();}}
  assert.deepEqual(calls.map(c=>c.url),['/student/badges/catalog','/admin/preview/student/badges/catalog','/admin/acting/student/badges/catalog']);
  assert.ok(calls.every(c=>c.method==='get'));assert.ok(calls.slice(1).every(c=>c.headers.get('X-Acting-Session')==='synthetic-session'));
 }finally{api.defaults.adapter=adapter;}
});
test('badge grids use one/two/three columns and recognizable muted locked medals',async()=>{
 const css=await readFile(new URL('../src/pages/student/styles/Badges.module.css',import.meta.url),'utf8');
 assert.match(css,/grid-template-columns: minmax\(0, 1fr\)/);assert.match(css,/@media \(min-width: 601px\)/);assert.match(css,/repeat\(2, minmax\(0, 1fr\)\)/);assert.match(css,/@media \(min-width: 1100px\)/);assert.match(css,/repeat\(3, minmax\(0, 1fr\)\)/);
 assert.match(css,/filter: grayscale\(1\)/);assert.match(css,/opacity: \.8/);assert.doesNotMatch(css,/opacity: 0[; ]/);
});

test('artwork maps only matching backend codes and never guesses from titles',async()=>{
 const {getBadgeArtwork}=await server.ssrLoadModule('/src/utils/badgeArtwork.js');
 const backend=await readFile(new URL('../HALO/src/main/java/com/ptc/halo/enums/BadgeType.java',import.meta.url),'utf8');
 for(const name of ['first-step','first-try','knowledge-seeker','perfect-score','never-give-up','comeback-stronger','module-finisher','subject-master','getting-started','dedicated-learner','assessment-ace','halo-achiever']){
  const code=name.toUpperCase().replaceAll('-','_');assert.ok(backend.includes(code+'('));assert.ok(getBadgeArtwork(code).endsWith(name+'.png'));
 }
 for(const code of ['UNKNOWN','HELPFUL_PEER','toString',undefined])assert.equal(getBadgeArtwork(code),null);
});
test('artwork preserves backend titles, counts, statuses and ownership context in all modes',async()=>{
 for(const mode of [{acting:false},{acting:true,preview:true,basePath:'/admin/student-preview/test'},{acting:true,preview:false,basePath:'/admin/student-mode/test'}]){
  const catalog=[{badgeType:'FIRST_STEP',badgeName:'First Step',description:'Original requirement'},{badgeType:'PERFECT_SCORE',badgeName:'Perfect Score',description:'Perfect requirement'},{badgeType:'UNKNOWN',badgeName:'First Step',description:'Not mapped by title'}];
  const view=await mount({getBadgesData:async()=>[{...catalog[0],id:9,badgeName:'Historical title'}],getBadgeCatalog:async()=>catalog},mode);
  try{
   assert.equal(view.host.querySelectorAll('li').length,3);assert.equal(view.host.querySelectorAll('li img').length,2);
   const earned=section(view.host,'Earned').querySelector('li');assert.match(earned.className,/earnedCard/);assert.doesNotMatch(earned.className,/lockedCard/);
   assert.equal(earned.querySelector('img').alt,'Historical title badge');assert.match(earned.textContent,/Original requirement/);
   const locked=section(view.host,'Badges to Unlock').querySelectorAll('li');assert.match(locked[0].className,/lockedCard/);assert.ok(locked[0].querySelector('img').src.endsWith('perfect-score.png'));
   assert.equal(locked[1].querySelector('img'),null);assert.equal(locked[1].querySelector('svg').getAttribute('aria-hidden'),'true');
   assert.match(view.host.textContent,/1 earned/);assert.match(view.host.textContent,/2 remaining/);assert.match(locked[0].textContent,/Perfect requirement/);
  }finally{await view.close();}
 }
});
test('artwork remains uncropped with responsive dimensions and no motion',async()=>{
 const css=await readFile(new URL('../src/pages/student/styles/Badges.module.css',import.meta.url),'utf8');
 assert.match(css,/object-fit: contain/);for(const size of [140,120,110])assert.ok(css.includes('width: '+size+'px; height: '+size+'px;'));
 assert.match(css,/\.lockedCard \.badgeIcon[^}]+grayscale\(1\) brightness\(\.9\)/);assert.doesNotMatch(css,/animation:|transition:/);
});

test('every current backend badge renders artwork in earned and locked sections in all modes',async()=>{
 const backend=await readFile(new URL('../HALO/src/main/java/com/ptc/halo/enums/BadgeType.java',import.meta.url),'utf8');
 const catalog=[...backend.matchAll(/([A-Z_]+)\("([^"]+)", "([^"]+)"\)/g)].map(([,badgeType,badgeName,description])=>({badgeType,badgeName,description}));
 assert.equal(catalog.length,12);
 for(const mode of [{acting:false},{acting:true,preview:true},{acting:true,preview:false}]){
  for(const earned of [[],catalog.map((badge,id)=>({...badge,id}))]){
   const view=await mount({getBadgesData:async()=>earned,getBadgeCatalog:async()=>catalog},mode);
   try{
    const cards=[...view.host.querySelectorAll('li')];assert.equal(cards.length,12);assert.equal(view.host.querySelectorAll('li svg').length,0);
    cards.forEach((card,index)=>{
     const badge=catalog[index],img=card.querySelector('img');assert.ok(img.src.endsWith(badge.badgeType.toLowerCase().replaceAll('_','-')+'.png'));
     assert.equal(img.alt,badge.badgeName+' badge');assert.equal(card.querySelector('p').textContent,badge.description);
     assert.match(card.className,earned.length?/earnedCard/:/lockedCard/);
     assert.equal(card.lastElementChild.textContent,earned.length?'Earned':'Locked');
    });
    assert.match(view.host.querySelector('header').textContent,new RegExp(earned.length+' earned'));
   }finally{await view.close();}
  }
 }
});
