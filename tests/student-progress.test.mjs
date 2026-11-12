import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';
let dom, server, createRoot, Router, AuthContext, Workflow, Progress;
const h = React.createElement;
before(async () => {
  dom = new JSDOM('<html><body></body></html>', { url: 'http://localhost' });
  for (const key of ['window', 'document', 'HTMLElement', 'Event', 'MouseEvent', 'localStorage', 'sessionStorage']) globalThis[key] = dom.window[key];
  globalThis.IS_REACT_ACT_ENVIRONMENT = true;
  ({ createRoot } = await import('react-dom/client')); Router = await import('react-router-dom');
  server = await createServer({ server: { middlewareMode: true, hmr: false }, appType: 'custom' });
  ({ AuthContext } = await server.ssrLoadModule('/src/context/login/useAuth.js'));
  ({ StudentWorkflowContext: Workflow } = await server.ssrLoadModule('/src/context/student/useStudentWorkflow.js'));
  Progress = (await server.ssrLoadModule('/src/pages/student/components/Progress.jsx')).default;
});
after(async () => { await server?.close(); dom?.window.close(); });
const subjects = [
  { subjectId: 1, subjectName: 'Hospitality Leadership', subjectCode: 'HRM101', eligibleModuleCount: 3, completedWeeks: 1, progressPercentage: 33 },
  { subjectId: 2, subjectName: 'Food Operations', subjectCode: 'FOOD202', eligibleModuleCount: 1, completedWeeks: 1, progressPercentage: 100 },
];
async function mount(rows = subjects, basePath = '/student') {
  let calls = 0;
  const host = document.createElement('div'); document.body.append(host); const root = createRoot(host);
  const acting = basePath !== '/student';
  await act(async () => root.render(h(AuthContext.Provider, { value: { user: { name: 'Synthetic Student' }, logout() {} } },
    h(Workflow.Provider, { value: { api: { getSubjectsData: async () => { calls++; return rows; } }, basePath, acting, preview: basePath.includes('preview'), target: { name: 'Synthetic target' }, leave() {} } },
      h(Router.MemoryRouter, null, h(Progress))))));
  return { host, calls: () => calls, close: async () => { await act(async () => root.unmount()); host.remove(); } };
}
async function search(host, value) {
  const input = host.querySelector('input[type="search"]');
  await act(async () => {
    Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set.call(input, value);
    input.dispatchEvent(new window.Event('input', { bubbles: true }));
  });
}

for (const count of [0, 1, 5, 6, 10, 21]) test(`pagination defaults to five subjects out of ${count}`, async () => {
  const page = await mount(Array.from({length:count}, (_,i)=>({...subjects[0],subjectId:i,subjectName:`Subject ${i}`})));
  try {
    assert.equal(page.host.querySelectorAll('progress').length,Math.min(5,count));
    const nav=page.host.querySelector('[aria-label="Subject Progress pagination"]');
    assert.match(nav.textContent,new RegExp(`of ${count} subjects`));
    assert.equal(nav.querySelector('button').disabled,true);
    assert.equal([...nav.querySelectorAll('button')].at(-1).disabled,count<=5);
  } finally {await page.close();}
});
test('pages, size and search combine locally', async () => {
  const page=await mount(Array.from({length:21},(_,i)=>({...subjects[0],subjectId:i,subjectName:`Subject ${i}`,subjectCode:i<12?'MATCH':'OTHER'})));
  try {
    const nav=()=>page.host.querySelector('[aria-label="Subject Progress pagination"]');
    const click=async label=>act(async()=>[...nav().querySelectorAll('button')].find(b=>b.textContent===label).click());
    await click('Next');assert.match(nav().textContent,/Showing 6–10 of 21/);
    await click('Previous');await click('5');assert.match(nav().textContent,/Showing 21–21/);
    await search(page.host,'MATCH');assert.match(nav().textContent,/Showing 1–5 of 12/);
    await click('Next');
    for(const size of [10,20,5]){
      await act(async()=>{const select=nav().querySelector('select');select.value=String(size);select.dispatchEvent(new Event('change',{bubbles:true}));});
      assert.match(nav().textContent,/Page 1 of/);assert.equal(page.host.querySelectorAll('progress').length,Math.min(size,12));
    }
    await search(page.host,'nothing');assert.match(nav().textContent,/Showing 0–0 of 0/);assert.equal(page.calls(),1);
  }finally{await page.close();}
});
test('data shrink clamps the page without restoring stale pages',async()=>{
  const {useSubjectPagination}=await server.ssrLoadModule('/src/hooks/useSubjectPagination.js');
  const host=document.createElement('div');document.body.append(host);const root=createRoot(host);let state;
  function Probe({count}){state=useSubjectPagination(Array.from({length:count},(_,i)=>i));return h('p',null,state.page);}
  try{
    await act(async()=>root.render(h(Probe,{count:21})));await act(async()=>state.setPage(5));
    await act(async()=>root.render(h(Probe,{count:6})));assert.equal(state.page,2);assert.deepEqual(state.items,[5]);
    await act(async()=>root.render(h(Probe,{count:21})));assert.equal(state.page,2);
    await act(async()=>root.render(h(Probe,{count:0})));assert.equal(state.page,1);
  }finally{await act(async()=>root.unmount());host.remove();}
});
test('overall ring uses unchanged backend count formula and subject bars retain authoritative percentages', async () => {
  const page = await mount(); try {
    const ring = page.host.querySelector('[role="progressbar"]');
    assert.equal(ring.getAttribute('aria-valuenow'), '50'); assert.match(ring.textContent, /50%/);
    assert.equal(ring.querySelector('svg').getAttribute('viewBox'), '0 0 140 140');
    assert.equal(ring.querySelectorAll('circle').length, 2);
    assert.deepEqual([...page.host.querySelectorAll('progress')].map(p => p.value), [33, 100]);
    assert.match(page.host.textContent, /Modules completed/); assert.match(page.host.textContent, /HRM101/);
  } finally { await page.close(); }
});
for (const [label, query, expected] of [['title', 'Leadership', 'Hospitality Leadership'], ['code', 'FOOD202', 'Food Operations'], ['case', 'hRm101', 'Hospitality Leadership'], ['whitespace', '  food operations  ', 'Food Operations']]) {
  test(`search by ${label} filters locally without changing overall totals or original data`, async () => {
    const original = JSON.stringify(subjects), page = await mount(); try {
      await search(page.host, query);
      assert.deepEqual([...page.host.querySelectorAll('h3')].map(n => n.textContent), [expected]);
      assert.equal(page.host.querySelector('[role="progressbar"]').getAttribute('aria-valuenow'), '50');
      assert.equal(page.calls(), 1); assert.equal(JSON.stringify(subjects), original);
    } finally { await page.close(); }
  });
}
test('no matches differs from empty enrollment and clear restores results and keyboard focus', async () => {
  const page = await mount(); try {
    await search(page.host, 'not a subject'); assert.match(page.host.textContent, /No subjects found/);
    assert.doesNotMatch(page.host.textContent, /No subjects enrolled yet/);
    await act(async () => [...page.host.querySelectorAll('button')].find(b => b.textContent === 'Clear search').click());
    assert.equal(page.host.querySelectorAll('h3').length, 2); assert.equal(document.activeElement, page.host.querySelector('input'));
    await search(page.host, '   '); assert.equal(page.host.querySelectorAll('h3').length, 2); assert.equal(page.calls(), 1);
  } finally { await page.close(); }
  const empty = await mount([]); try { assert.match(empty.host.textContent, /No subjects enrolled yet/); assert.doesNotMatch(empty.host.textContent, /No subjects found/); } finally { await empty.close(); }
});
for (const base of ['/student', '/admin/student-preview', '/admin/student-support']) {
  test(`existing subjects navigation stays scoped to ${base}`, async () => {
    const page = await mount(subjects, base); try {
      assert.equal([...page.host.querySelectorAll('a')].find(a => a.textContent === 'View all subjects').getAttribute('href'), base + '/subjects/my');
    } finally { await page.close(); }
  });
}
test('ring, mobile cards and search retain responsive and theme-aware style constraints', () => {
  const css = readFileSync('src/pages/student/styles/Progress.module.css', 'utf8');
  assert.match(css, /aspect-ratio: 1/); assert.match(css, /\.ringSvg[^}]*width: 100%[^}]*height: 100%/);
  assert.match(css, /max-width: 768px/); assert.match(css, /max-width: 480px/);
  assert.match(css, /overflow-wrap: anywhere/); assert.match(css, /focus-visible/);
  assert.match(css, /var\(--color-text-primary\)/); assert.match(css, /var\(--color-bg-stat-card\)/);
});

test('overview keeps the unboxed ring column and uses equal Dashboard statistic cards', async () => {
  const page = await mount();
  try {
    const ring = page.host.querySelector('[role="progressbar"]');
    const overall = ring.parentElement;
    assert.equal(overall.querySelector('h2').textContent, 'Overall Progress');
    assert.equal(overall.children[0], ring);
    const stats = overall.nextElementSibling;
    assert.equal(stats.children.length, 2);
    for (const [index, card] of [...stats.children].entries()) {
      assert.equal(card.children.length, 3);
      assert.equal(card.children[0].querySelector('svg').getAttribute('width'), '20');
      assert.equal(card.children[1].textContent, index === 0 ? '2' : '4');
      assert.match(card.className, /statCard/);
    }
    assert.equal(stats.children[0].className, stats.children[1].className);
    const css = readFileSync('src/pages/student/styles/Progress.module.css', 'utf8');
    const overallRule = css.match(/\.overallBlock\s*\{([^}]+)\}/)[1];
    assert.doesNotMatch(overallRule, /background|border|shadow/);
    assert.match(css, /composes: statCard from "\.\/StudentDashboard.module.css"/);
    assert.match(css, /grid-auto-rows: 1fr/);
    assert.doesNotMatch(css, /max-width: 560px/);
    assert.match(css, /max-width: 480px[^}]*\.statsGrid\s*\{ grid-template-columns: minmax\(0, 1fr\)/);
  } finally { await page.close(); }
});
