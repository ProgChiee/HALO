import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';

let dom, server, createRoot, Router, AuthContext, Workflow, Dashboard;
before(async () => {
  dom = new JSDOM('<html><body></body></html>', { url: 'http://localhost' });
  for (const key of ['window', 'document', 'HTMLElement', 'Event', 'localStorage', 'sessionStorage']) globalThis[key] = dom.window[key];
  globalThis.IS_REACT_ACT_ENVIRONMENT = true;
  window.matchMedia = () => ({ matches: false, addEventListener() {}, removeEventListener() {} });
  ({ createRoot } = await import('react-dom/client'));
  Router = await import('react-router-dom');
  server = await createServer({ server: { middlewareMode: true }, appType: 'custom' });
  ({ AuthContext } = await server.ssrLoadModule('/src/context/login/useAuth.js'));
  ({ StudentWorkflowContext: Workflow } = await server.ssrLoadModule('/src/context/student/useStudentWorkflow.js'));
  Dashboard = (await server.ssrLoadModule('/src/pages/student/components/StudentDashboard.jsx')).default;
});
after(async () => { await server.close(); dom.window.close(); });

test('Continue resumes persisted recent module after remount and navigates to its subject/week', async () => {
  const workflow = { acting: false, basePath: '/student', api: {
    getDashboardData: async () => ({}),
    getSubjectsData: async () => [{ subjectId: 1, subjectName: 'Older subject', progressPercentage: 0 },
      { subjectId: 15, subjectName: 'Leadership', progressPercentage: 100 }],
    getSubjectWeeks: async id => [{ moduleId: id === 15 ? 42 : 1, weekId: id === 15 ? 18 : 1,
      weekNumber: 1, title: id === 15 ? 'Recent lesson' : 'Older lesson', unlocked: true,
      lessonAvailable: true, completed: id === 15, lastActivityAt: id === 15 ? '2026-10-26T01:00:00' : null }],
  } };
  function Location() { return React.createElement('output', null, Router.useLocation().pathname); }
  for (let mount = 0; mount < 2; mount++) {
    const host = document.createElement('div'); document.body.append(host); const root = createRoot(host);
    try {
      await act(async () => root.render(React.createElement(AuthContext.Provider, { value: { user: { name: 'Test' }, logout() {} } },
        React.createElement(Workflow.Provider, { value: workflow }, React.createElement(Router.MemoryRouter, null,
          React.createElement(Dashboard), React.createElement(Location))))));
      assert.match(host.textContent, /Week 1: Recent lesson/);
      await act(async () => [...host.querySelectorAll('button')].find(b => b.textContent === 'Continue').click());
      assert.equal(host.querySelector('output').textContent, '/student/lesson/15/18');
    } finally { await act(async () => root.unmount()); host.remove(); }
  }
});

for (const [name, score, expected] of [
  ['null', { latestAssessmentScore: null }, 'No assessment yet'],
  ['undefined', { latestAssessmentScore: undefined }, 'No assessment yet'],
  ['missing field', {}, 'No assessment yet'],
  ['actual zero', { latestAssessmentScore: 0 }, '0%'],
  ['positive', { latestAssessmentScore: 85 }, '85%'],
  ['perfect', { latestAssessmentScore: 100 }, '100%'],
]) {
  test(`Dashboard renders ${name} assessment score correctly`, async () => {
    const summary = { completedModules: 2, passedAssessments: 1, totalBadges: 3, ...score };
    const workflow = { acting: false, basePath: '/student', api: {
      getDashboardData: async () => summary,
      getSubjectsData: async () => [],
      getSubjectWeeks: async () => [],
    } };
    const host = document.createElement('div'); document.body.append(host); const root = createRoot(host);
    try {
      await act(async () => root.render(React.createElement(AuthContext.Provider, { value: { user: { name: 'Synthetic Student' }, logout() {} } },
        React.createElement(Workflow.Provider, { value: workflow }, React.createElement(Router.MemoryRouter, null, React.createElement(Dashboard))))));
      const card = label => [...host.querySelectorAll('p')].find(p => p.textContent === label).parentElement;
      assert.equal(card('Latest assessment score').querySelector('p').textContent, expected);
      assert.equal(card('Modules completed').querySelector('p').textContent, '2');
      assert.equal(card('Assessments passed').querySelector('p').textContent, '1');
      assert.equal(card('Badges earned').querySelector('p').textContent, '3');
      assert.deepEqual(summary, { completedModules: 2, passedAssessments: 1, totalBadges: 3, ...score });
    } finally { await act(async () => root.unmount()); host.remove(); }
  });
}
