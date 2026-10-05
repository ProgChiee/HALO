import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';
import { logStudentError } from '../src/utils/studentDiagnostics.js';

const secret = 'SECRET_TOKEN_PASSWORD_PERSONAL_DATA';
const failure = { message: secret, config: { headers: { Authorization: secret }, data: { password: secret, answers: [{ questionId: 42, answer: secret }] } }, response: { status: 403, data: { code: 'ACCESS_DENIED', message: secret, user: secret } } };
test('diagnostics only contain fixed labels, numeric statuses and allowlisted codes', () => {
  const original = console.error, calls = [];
  console.error = (...args) => calls.push(args);
  try {
    logStudentError('load-dashboard', failure);
    assert.deepEqual(calls[0], ['[HALO Student]', { operation: 'load-dashboard', status: 403, code: 'ACCESS_DENIED' }]);
    logStudentError(secret, { response: { status: secret, data: { code: secret } } });
    logStudentError('load-profile', null);
    assert.deepEqual(calls[1][1], { operation: 'request-failed' });
    assert.ok(!JSON.stringify(calls).includes(secret));
  } finally { console.error = original; }
});

let dom, server, createRoot, Router, AuthContext, ToastContext, api;
before(async () => {
  dom = new JSDOM('<html><body></body></html>', { url: 'http://localhost' });
  for (const key of ['window', 'document', 'HTMLElement', 'Event', 'localStorage', 'sessionStorage']) globalThis[key] = dom.window[key];
  globalThis.IS_REACT_ACT_ENVIRONMENT = true;
  ({ createRoot } = await import('react-dom/client'));
  Router = await import('react-router-dom');
  server = await createServer({ server: { middlewareMode: true }, appType: 'custom' });
  ({ AuthContext } = await server.ssrLoadModule('/src/context/login/useAuth.js'));
  ({ ToastContext } = await server.ssrLoadModule('/src/context/notifications/useToast.js'));
  api = (await server.ssrLoadModule('/src/services/apiClient.js')).default;
});
after(async () => { await server?.close(); dom?.window.close(); });

test('dashboard and profile retain error UI while logging only sanitized diagnostics', async () => {
  for (const name of ['StudentDashboard', 'Subjects', 'Quiz', 'Progress', 'Badges', 'Profile']) {
    const Component = (await server.ssrLoadModule(`/src/pages/student/components/${name}.jsx`)).default;
    const host = document.createElement('div'); document.body.append(host); const root = createRoot(host);
    const originalError = console.error, originalWarn = console.warn, adapter = api.defaults.adapter, calls = [];
    console.error = (...args) => calls.push(args);
    console.warn = (...args) => calls.push(args);
    api.defaults.adapter = async config => { throw { ...failure, config: { ...config, headers: { Authorization: secret }, data: secret } }; };
    try {
      await act(async () => root.render(React.createElement(AuthContext.Provider, { value: { user: { name: 'Student' }, logout() {} } },
        React.createElement(ToastContext.Provider, { value: { showToast() {} } }, React.createElement(Router.MemoryRouter, {}, React.createElement(Component))))));
      assert.match(host.textContent, /couldn't load|could not load|unable to load|failed to load/i);
      assert.ok(calls.some(call => call[0] === '[HALO Student]' && call[1].status === 403));
      assert.ok(!JSON.stringify(calls).includes(secret));
    } finally {
      await act(async () => root.unmount()); host.remove(); console.error = originalError; console.warn = originalWarn; api.defaults.adapter = adapter;
    }
  }
});


test('Progress uses eligible module counts instead of week totals and handles zero', async () => {
  const Component = (await server.ssrLoadModule('/src/pages/student/components/Progress.jsx')).default;
  for (const count of [2, 0]) {
    const host = document.createElement('div'); document.body.append(host); const root = createRoot(host);
    const adapter = api.defaults.adapter;
    api.defaults.adapter = async config => ({ status: 200, headers: {}, config, data: [{ subjectId: 1, subjectName: 'Subject', totalWeeks: 8, eligibleModuleCount: count, completedWeeks: count ? 1 : 0, progressPercentage: count ? 50 : 0 }] });
    try {
      await act(async () => root.render(React.createElement(AuthContext.Provider, { value: { user: { name: 'Student' }, logout() {} } }, React.createElement(Router.MemoryRouter, {}, React.createElement(Component)))));
      const label = text => [...host.querySelectorAll('p')].find(node => node.textContent === text);
      assert.equal(label('Total modules').previousElementSibling.textContent, String(count));
      assert.equal(label('Modules completed').previousElementSibling.textContent, count ? '1' : '0');
      assert.match(host.textContent, count ? /50%/ : /0%/);
      assert.doesNotMatch(host.textContent, /NaN|Infinity/);
    } finally { await act(async () => root.unmount()); host.remove(); api.defaults.adapter = adapter; }
  }
});

test('LessonChat prepends older messages without duplicates and keeps new replies', async () => {
  dom.window.HTMLElement.prototype.scrollIntoView = () => {};
  const Component = (await server.ssrLoadModule('/src/pages/student/components/LessonChat.jsx')).default;
  const host = document.createElement('div'); document.body.append(host); const root = createRoot(host);
  const adapter = api.defaults.adapter, calls = [];
  api.defaults.adapter = async config => {
    calls.push(config);
    let data;
    if (config.url.includes('/week/')) data = { id: 10, weekId: 1, status: 'APPROVED', aiGenerationStatus: 'COMPLETED' };
    else if (config.url.includes('/open/')) data = { sessionId: 20, moduleId: 10, hasOlder: true, nextBeforeId: 30, messages: [{ id: 30, sender: 'HALO', message: 'Recent message' }] };
    else if (config.url.includes('/message/')) data = { sessionId: 20, moduleId: 10, haloMessage: 'New reply' };
    else data = { sessionId: 20, moduleId: 10, hasOlder: false, nextBeforeId: null, messages: [{ id: 1, sender: 'HALO', message: 'Older message' }, { id: 30, sender: 'HALO', message: 'Recent message' }] };
    return { status: 200, headers: {}, config, data };
  };
  try {
    await act(async () => root.render(React.createElement(AuthContext.Provider, { value: { user: { name: 'Student' }, logout() {} } }, React.createElement(ToastContext.Provider, { value: { showToast() {} } }, React.createElement(Router.MemoryRouter, { initialEntries: ['/student/lesson/1/1'] }, React.createElement(Router.Routes, {}, React.createElement(Router.Route, { path: '/student/lesson/:topicId/:weekId', element: React.createElement(Component) })))))));
    await act(async () => [...host.querySelectorAll('button')].find(b => b.textContent === 'Load older messages').click());
    assert.equal(calls.at(-1).params.beforeId, 30);
    assert.equal(host.textContent.split('Recent message').length - 1, 1);
    assert.ok(host.textContent.indexOf('Older message') < host.textContent.indexOf('Recent message'));
    assert.ok(!host.textContent.includes('Load older messages'));
    await act(async () => {
      const input = host.querySelector('input');
      Object.getOwnPropertyDescriptor(dom.window.HTMLInputElement.prototype, 'value').set.call(input, 'Question');
      input.dispatchEvent(new dom.window.Event('input', { bubbles: true }));
    });
    await act(async () => host.querySelector('form').dispatchEvent(new dom.window.Event('submit', { bubbles: true, cancelable: true })));
    assert.ok(host.textContent.indexOf('New reply') > host.textContent.indexOf('Recent message'));
  } finally { await act(async () => root.unmount()); host.remove(); api.defaults.adapter = adapter; }
});

test('Student dashboard sections load and retry independently', async () => {
  const Component = (await server.ssrLoadModule('/src/pages/student/components/StudentDashboard.jsx')).default;
  for (const failing of ['stats', 'subjects', 'continue']) {
    const host = document.createElement('div'); document.body.append(host); const root = createRoot(host);
    const adapter = api.defaults.adapter, calls = [];
    let fail = true;
    api.defaults.adapter = async config => {
      calls.push(config.url);
      const section = config.url.endsWith('/dashboard') ? 'stats' : config.url.endsWith('/subjects') ? 'subjects' : 'continue';
      if (fail && section === failing) throw { response: { status: 500, data: { code: 'INTERNAL_SERVER_ERROR' } } };
      const data = section === 'stats' ? { completedModules: 7, passedAssessments: 2, totalBadges: 3, latestAssessmentScore: 80 }
        : section === 'subjects' ? [{ subjectId: 1, subjectName: 'Hospitality', progressPercentage: 0, completedWeeks: 0, totalWeeks: 1 }]
        : [{ weekId: 1, weekNumber: 1, title: 'Published topic', moduleId: 10, lessonAvailable: true, unlocked: true, completed: false }];
      return { status: 200, headers: {}, config, data };
    };
    try {
      await act(async () => root.render(React.createElement(AuthContext.Provider, { value: { user: { name: 'Student' }, logout() {} } }, React.createElement(Router.MemoryRouter, {}, React.createElement(Component)))));
      if (failing !== 'stats') assert.match(host.textContent, /Modules completed/);
      if (failing !== 'subjects') assert.match(host.textContent, /Hospitality/);
      const beforeStats = calls.filter(url => url.endsWith('/dashboard')).length;
      const beforeSubjects = calls.filter(url => url.endsWith('/subjects')).length;
      fail = false;
      const label = failing === 'stats' ? 'Retry statistics' : failing === 'subjects' ? 'Retry subjects' : 'Retry Continue';
      await act(async () => [...host.querySelectorAll('button')].find(b => b.textContent === label).click());
      assert.match(host.textContent, /Modules completed/); assert.match(host.textContent, /Published topic/);
      assert.equal(calls.filter(url => url.endsWith('/dashboard')).length, beforeStats + (failing === 'stats' ? 1 : 0));
      assert.equal(calls.filter(url => url.endsWith('/subjects')).length, beforeSubjects + (failing === 'subjects' ? 1 : 0));
    } finally { await act(async () => root.unmount()); host.remove(); api.defaults.adapter = adapter; }
  }
});

test('slow Continue does not block stats and its request is aborted on unmount', async () => {
  const Component = (await server.ssrLoadModule('/src/pages/student/components/StudentDashboard.jsx')).default;
  const host = document.createElement('div'); document.body.append(host); const root = createRoot(host);
  const adapter = api.defaults.adapter;
  let finish, signal;
  api.defaults.adapter = async config => {
    if (config.url.endsWith('/weeks')) { signal = config.signal; await new Promise(resolve => { finish = resolve; }); }
    const data = config.url.endsWith('/dashboard') ? { completedModules: 1 } : config.url.endsWith('/subjects') ? [{ subjectId: 1, subjectName: 'Subject', progressPercentage: 0 }] : [];
    return { status: 200, headers: {}, config, data };
  };
  try {
    await act(async () => root.render(React.createElement(AuthContext.Provider, { value: { user: { name: 'Student' }, logout() {} } }, React.createElement(Router.MemoryRouter, {}, React.createElement(Component)))));
    assert.match(host.textContent, /Modules completed/); assert.match(host.textContent, /Finding your next lesson/);
    await act(async () => root.unmount()); assert.ok(signal.aborted);
    await act(async () => finish()); assert.equal(host.textContent, '');
  } finally { host.remove(); api.defaults.adapter = adapter; }
});

test('Continue label and navigation use the selected lesson Subject', async () => {
  const Component = (await server.ssrLoadModule('/src/pages/student/components/StudentDashboard.jsx')).default;
  function Location() { return React.createElement('output', { id: 'destination' }, Router.useLocation().pathname); }
  for (const includeUnavailableFirst of [true, false]) {
    const host = document.createElement('div'); document.body.append(host); const root = createRoot(host);
    const adapter = api.defaults.adapter;
    api.defaults.adapter = async config => {
      let data;
      if (config.url.endsWith('/dashboard')) data = { completedModules: 0, passedAssessments: 0, totalBadges: 0 };
      else if (config.url.endsWith('/subjects')) data = [
        ...(includeUnavailableFirst ? [{ subjectId: 1, subjectName: 'Unavailable Subject', progressPercentage: 0 }] : []),
        { subjectId: 2, subjectName: 'Actual Subject', progressPercentage: 0 },
      ];
      else if (config.url.includes('/subjects/1/')) data = [{ weekId: 11, lessonAvailable: false, unlocked: true, completed: false }];
      else data = [{ weekId: 22, weekNumber: 2, title: 'Actual Lesson', moduleId: 222, lessonAvailable: true, unlocked: true, completed: false }];
      return { status: 200, headers: {}, config, data };
    };
    try {
      await act(async () => root.render(React.createElement(AuthContext.Provider, { value: { user: { name: 'Student' }, logout() {} } }, React.createElement(Router.MemoryRouter, {}, React.createElement(Component), React.createElement(Location)))));
      const button = [...host.querySelectorAll('button')].find(node => node.textContent === 'Continue');
      const card = button.parentElement;
      assert.match(card.textContent, /Week 2: Actual Lesson/);
      assert.match(card.textContent, /Actual Subject/);
      assert.doesNotMatch(card.textContent, /Unavailable Subject/);
      await act(async () => button.click());
      assert.equal(host.querySelector('#destination').textContent, '/student/lesson/2/22');
    } finally { await act(async () => root.unmount()); host.remove(); api.defaults.adapter = adapter; }
  }
});

test('Quiz recovers uncertain and already-submitted results without another POST', async () => {
 const Component=(await server.ssrLoadModule('/src/pages/student/components/Quiz.jsx')).default;
 for(const mode of ['normal','lost','unsubmitted','already','validation','unmount']) {
  const host=document.createElement('div');document.body.append(host);const root=createRoot(host);
  const adapter=api.defaults.adapter,calls=[],toasts=[];let finish;let unmounted=false;
  api.defaults.adapter=async config=>{
   calls.push(config.url);let data;
   if(config.url.includes('/week/')) data={id:10};
   else if(config.url.includes('/status/')) data={assessmentExists:true,assessmentAvailable:true,canTakeAssessment:true};
   else if(config.url.includes('/start/')) data={attemptId:20};
   else if(config.url.includes('/submit/')) {
    if(mode==='already') throw {response:{status:409,data:{code:'ASSESSMENT_ALREADY_SUBMITTED'}}};
    if(mode==='validation') throw {response:{status:400,data:{code:'INVALID_ANSWER_SET'}}};
    if(mode!=='normal') throw {code:'ECONNABORTED'};
    data={attemptId:20,score:100,passed:true};
   } else if(config.url.includes('/result/')) {
    if(mode==='unsubmitted') throw {response:{status:409,data:{code:'ASSESSMENT_NOT_SUBMITTED'}}};
    if(mode==='unmount') await new Promise(resolve=>{finish=resolve;});
    data={attemptId:20,score:100,passed:true,feedback:[]};
   } else data={title:'Quiz',passingScore:70,questions:[{id:1,questionText:'Question',optionA:'Answer'}]};
   return {status:200,headers:{},config,data};
  };
  try {
   await act(async()=>root.render(React.createElement(AuthContext.Provider,{value:{user:{name:'Student'},logout(){}}},React.createElement(ToastContext.Provider,{value:{showToast:(...args)=>toasts.push(args)}},React.createElement(Router.MemoryRouter,{},React.createElement(Component))))));
   await act(async()=>[...host.querySelectorAll('button')].find(b=>b.textContent==='Start quiz').click());
   await act(async()=>[...host.querySelectorAll('button')].find(b=>b.textContent.includes('Answer')).click());
   await act(async()=>[...host.querySelectorAll('button')].find(b=>b.textContent==='Submit quiz').click());
   assert.equal(calls.filter(url=>url.includes('/submit/')).length,1);
   if(mode==='unmount') {
    assert.ok([...host.querySelectorAll('button')].every(b=>!b.textContent.includes('Submit quiz')||b.disabled));
    await act(async()=>root.unmount());unmounted=true;await act(async()=>finish());assert.equal(toasts.length,0);
   } else if(mode==='unsubmitted'||mode==='validation') {assert.equal(toasts.length,1);assert.equal(toasts[0][1],'error');}
   else {assert.match(host.textContent,/100%/);assert.equal(toasts.length,1);assert.equal(toasts[0][1],'success');}
   if(mode==='validation')assert.equal(calls.filter(url=>url.includes('/result/')).length,0);
  } finally {if(!unmounted)await act(async()=>root.unmount());host.remove();api.defaults.adapter=adapter;}
 }
});

test('Mentor reconciles lost responses and reuses request ID for manual retry', async () => {
 dom.window.HTMLElement.prototype.scrollIntoView=()=>{};
 const Component=(await server.ssrLoadModule('/src/pages/student/components/LessonChat.jsx')).default;
 for(const mode of ['committed','pending','unmount']) {
  const host=document.createElement('div');document.body.append(host);const root=createRoot(host);const adapter=api.defaults.adapter;
  const keys=[],toasts=[];let finish,unmounted=false;
  api.defaults.adapter=async config=>{
   let data;
   if(config.url.includes('/week/'))data={id:10,weekId:1,status:'APPROVED',aiGenerationStatus:'COMPLETED'};
   else if(config.url.includes('/open/'))data={sessionId:20,moduleId:10,messages:[]};
   else if(config.method==='post') {keys.push(JSON.parse(config.data).requestId);if(keys.length===1)throw {code:'ECONNABORTED'};data={sessionId:20,moduleId:10,haloMessage:'Saved reply'};}
   else {if(mode==='unmount')await new Promise(resolve=>{finish=resolve;});if(mode==='pending')throw {response:{status:404,data:{code:'EXCHANGE_NOT_FOUND'}}};data={sessionId:20,moduleId:10,haloMessage:'Saved reply'};}
   return {status:200,headers:{},config,data};
  };
  try {
   await act(async()=>root.render(React.createElement(AuthContext.Provider,{value:{user:{name:'Student'},logout(){}}},React.createElement(ToastContext.Provider,{value:{showToast:(...args)=>toasts.push(args)}},React.createElement(Router.MemoryRouter,{initialEntries:['/student/lesson/1/1']},React.createElement(Router.Routes,{},React.createElement(Router.Route,{path:'/student/lesson/:topicId/:weekId',element:React.createElement(Component)})))))));
   await act(async()=>{const input=host.querySelector('input');Object.getOwnPropertyDescriptor(dom.window.HTMLInputElement.prototype,'value').set.call(input,'Question');input.dispatchEvent(new dom.window.Event('input',{bubbles:true}));});
   const send=()=>host.querySelector('form').dispatchEvent(new dom.window.Event('submit',{bubbles:true,cancelable:true}));
   await act(async()=>send());assert.equal(keys.length,1);assert.ok(keys[0]);
   if(mode==='unmount') {assert.ok(host.querySelector('button[aria-label="Send message"]').disabled);await act(async()=>root.unmount());unmounted=true;await act(async()=>finish());assert.equal(toasts.length,0);}
   else {
    if(mode==='pending'){assert.equal(host.querySelector('input').value,'Question');await act(async()=>send());assert.equal(keys.length,2);assert.equal(keys[0],keys[1]);}
    assert.equal(host.textContent.split('Saved reply').length-1,1);
    if(mode==='committed')assert.equal(toasts.length,0);
   }
  }finally{if(!unmounted)await act(async()=>root.unmount());host.remove();api.defaults.adapter=adapter;}
 }
});
