import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'vite';

let server;
let api;
let auth;
let admin;
let professor;
let quiz;
let mentor;
let captured;
const responseData = { marker: 'response preserved' };

test('Admin request failures log only safe diagnostics and preserve errors for the UI', async () => {
  const originalAdapter = api.defaults.adapter;
  const originalWarn = console.warn;
  const originalError = console.error;
  const originalLog = console.log;
  const logged = [];
  const rawLogs = [];
  console.warn = (...args) => logged.push(args);
  console.error = console.log = (...args) => rawLogs.push(args);
  api.defaults.adapter = async config => {
    throw {
      config: { ...config, url: config.url + '?resetToken=PRIVATE_RESET', headers: { Authorization: 'Bearer PRIVATE_JWT' }, data: { password: 'PRIVATE_PASSWORD' } },
      request: { personalInformation: 'PRIVATE_PERSON' },
      response: { status: 409, data: { code: 'EMAIL_ALREADY_EXISTS', message: 'PRIVATE_RESPONSE' } },
    };
  };
  try {
    const calls = [
      () => admin.getAdminDashboardData(), () => admin.getAdminRecentActivity(),
      () => admin.getProfessors(),
      () => admin.createProfessor({ name: 'Fixture', email: 'fixture@example.test', password: 'PRIVATE_PASSWORD', professorId: 'P1' }),
      () => admin.updateProfessor(1, { name: 'Fixture', email: 'fixture@example.test', professorId: 'P1' }),
      () => admin.toggleProfessorStatus(1), () => admin.getAdminProfile(),
      () => auth.changePassword('PRIVATE_CURRENT', 'PRIVATE_PASSWORD'),
    ];
    const { apiErrorMessage } = await server.ssrLoadModule('/src/utils/apiErrors.js');
    for (const call of calls) {
      await assert.rejects(call(), error => {
        assert.equal(error.response.status, 409);
        assert.equal(apiErrorMessage(error), 'An account with this email already exists.');
        return true;
      });
    }
    assert.equal(logged.length, calls.length);
    assert.deepEqual(rawLogs, []);
    for (const [label, diagnostic] of logged) {
      assert.equal(label, '[HALO API]');
      assert.deepEqual(Object.keys(diagnostic).sort(), ['code', 'endpoint', 'method', 'status']);
      assert.equal(diagnostic.status, 409);
      assert.equal(diagnostic.code, 'EMAIL_ALREADY_EXISTS');
      assert.ok(diagnostic.endpoint.startsWith('/'));
    }
    assert.doesNotMatch(JSON.stringify(logged), /PRIVATE_|Authorization|Bearer|fixture@example|resetToken/i);
  } finally {
    api.defaults.adapter = originalAdapter;
    console.warn = originalWarn;
    console.error = originalError;
    console.log = originalLog;
  }
});
function memoryStorage() {
  const values = new Map();
  return { getItem: (key) => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: (key) => values.delete(key) };
}

before(async () => {
  globalThis.localStorage = memoryStorage();
  globalThis.sessionStorage = memoryStorage();
  globalThis.window = new EventTarget();
  server = await createServer({ server: { middlewareMode: true }, appType: 'custom' });
  api = (await server.ssrLoadModule('/src/services/apiClient.js')).default;
  auth = await server.ssrLoadModule('/src/services/authService.js');
  admin = await server.ssrLoadModule('/src/services/admin/adminService.js');
  professor = await server.ssrLoadModule('/src/services/professor/professorService.js');
  quiz = await server.ssrLoadModule('/src/services/student/quizService.js');
  mentor = await server.ssrLoadModule('/src/services/student/aiMentorService.js');
  api.defaults.adapter = async (config) => {
    captured = config;
    return { data: responseData, status: 200, statusText: 'OK', headers: {}, config };
  };
});

after(async () => {
  await server?.close();
  delete globalThis.localStorage;
  delete globalThis.sessionStorage;
  delete globalThis.window;
});

test('auth sends exact backend request fields and keeps public requests free of bearer tokens', async () => {
  localStorage.setItem('halo_user', JSON.stringify({ name: 'Test', email: 'test@example.test', role: 'student' }));
  localStorage.setItem('halo_token', 'fake-test-token');
  assert.deepEqual(await auth.login('test@example.test', 'test-password'), responseData);
  assert.deepEqual(JSON.parse(captured.data), { email: 'test@example.test', password: 'test-password' });
  assert.equal(captured.headers.Authorization, undefined);
  await auth.register('Test', 'test@example.test', 'test-password', 'ST-1', 'A', 'FIRST_YEAR');
  assert.deepEqual(JSON.parse(captured.data), { name: 'Test', email: 'test@example.test', password: 'test-password', studentId: 'ST-1', section: 'A', yearLevel: 'FIRST_YEAR' });
  await auth.resetPassword('test@example.test', '123456', 'new-password');
  assert.deepEqual(JSON.parse(captured.data), { email: 'test@example.test', otp: '123456', newPassword: 'new-password' });
  await auth.changePassword('old-password', 'new-password');
  assert.equal(captured.headers.Authorization, 'Bearer fake-test-token');
});

test('monitoring uses the type query parameter and forwards cancellation', async () => {
  const signal = new AbortController().signal;
  await admin.getActivityLog({ role: 'PROFESSOR', activityType: 'MODULE', signal });
  assert.equal(captured.url, '/admin/activity-logs');
  assert.deepEqual(captured.params, { role: 'PROFESSOR', type: 'MODULE' });
  assert.equal(captured.signal, signal);
});

test('professor CRUD and module upload retain numeric week numbers and multipart field names', async () => {
  await professor.addWeek(7, { weekNumber: 2, title: 'Lesson two' });
  assert.equal(captured.url, '/professor/subjects/7/weeks');
  assert.deepEqual(JSON.parse(captured.data), { weekNumber: 2, title: 'Lesson two' });
  const file = new File(['test'], 'lesson.pdf', { type: 'application/pdf' });
  await professor.createLearningModule(7, { lessonText: 'Text', youtubeLink: 'https://youtube.com/watch?v=test', aiNotes: 'Notes', files: [file] });
  assert.ok(captured.data instanceof FormData);
  assert.deepEqual([...captured.data.keys()], ['weekId', 'lessonText', 'youtubeLink', 'aiNotes', 'files']);
  assert.equal(captured.data.get('weekId'), '7');
  await professor.approveLesson(9);
  assert.equal(captured.method, 'put');
  assert.equal(captured.url, '/professor/ai-learning-modules/9/approve');
});

test('quiz and mentor requests use the controller IDs and payload wrappers', async () => {
  const answers = [{ questionId: 6, answer: 'B' }];
  assert.deepEqual(await quiz.submitAttempt(22, answers), responseData);
  assert.equal(captured.url, '/student/assessment/submit/22');
  assert.deepEqual(JSON.parse(captured.data), { answers });
  await quiz.getAttemptResult(22);
  assert.equal(captured.method, 'get');
  assert.equal(captured.url, '/student/assessment/result/22');
  await mentor.openSession(9);
  assert.equal(captured.url, '/student/mentor/open/9');
  await mentor.sendMessage(12, 'Explain this');
  assert.equal(captured.url, '/student/mentor/message/12');
  assert.deepEqual(JSON.parse(captured.data), { message: 'Explain this' });
});

test('professor draft lookup preserves saved data, treats only 204 as empty, and forwards cancellation', async () => {
  const originalAdapter = api.defaults.adapter;
  const signal = new AbortController().signal;
  const draft = { id: 9, weekId: 7, status: 'PENDING', files: [{ id: 3, originalFileName: 'lesson.pdf' }] };
  try {
    api.defaults.adapter = async (config) => {
      captured = config;
      return { data: draft, status: 200, headers: {}, config };
    };
    assert.deepEqual(await professor.getLearningModuleByWeek(7, { signal }), draft);
    assert.equal(captured.url, '/professor/ai-learning-modules/week/7');
    assert.equal(captured.signal, signal);
    api.defaults.adapter = async (config) => ({ data: '', status: 204, headers: {}, config });
    assert.equal(await professor.getLearningModuleByWeek(7), null);
    api.defaults.adapter = async () => { throw Object.assign(new Error('Week not found'), { response: { status: 404 } }); };
    await assert.rejects(professor.getLearningModuleByWeek(7), /Week not found/);
  } finally {
    api.defaults.adapter = originalAdapter;
  }
});

test('existing draft updates use JSON and individual file uploads use singular file field', async () => {
  const materials = { lessonText: 'Updated', youtubeLink: '', aiNotes: '' };
  assert.deepEqual(await professor.updateLearningModule(9, materials), responseData);
  assert.equal(captured.method, 'put');
  assert.equal(captured.url, '/professor/ai-learning-modules/9');
  assert.deepEqual(JSON.parse(captured.data), materials);
  await professor.uploadModuleFile(9, new File(['pdf'], 'lesson.pdf', { type: 'application/pdf' }));
  assert.equal(captured.url, '/professor/ai-learning-modules/9/files');
  assert.deepEqual([...captured.data.keys()], ['file']);
  await professor.deleteModuleFile(3);
  assert.equal(captured.method, 'delete');
  assert.equal(captured.url, '/professor/ai-learning-modules/files/3');
});

test('role menus do not offer routes forbidden by SecurityConfig', async () => {
  const navigation = await server.ssrLoadModule('/src/data/navigationData.js');
  assert.ok(navigation.SUPERADMIN_NAV_ITEMS.every((item) => item.path.startsWith('/superadmin')));
  assert.ok(!navigation.ADMIN_NAV_ITEMS.some((item) => item.path === '/admin/subjects'));
});

test('401 from an old token does not clear a newer session; current token expiry does', async () => {
  const reject = api.interceptors.response.handlers[0].rejected;
  localStorage.setItem('halo_token', 'new-token');
  await assert.rejects(reject({ response: { status: 401, data: { code: 'INVALID_OR_EXPIRED_TOKEN' } }, config: { headers: { Authorization: 'Bearer old-token' } } }));
  assert.equal(localStorage.getItem('halo_token'), 'new-token');
  let notified = false;
  window.addEventListener('halo:session-expired', () => { notified = true; }, { once: true });
  await assert.rejects(reject({ response: { status: 401, data: { code: 'INVALID_OR_EXPIRED_TOKEN' } }, config: { headers: { Authorization: 'Bearer new-token' } } }));
  assert.equal(localStorage.getItem('halo_token'), null);
  assert.equal(notified, true);
});


test('mentor open uses POST with bearer token, actual module ID and cancellation', async () => {
  localStorage.setItem('halo_user', JSON.stringify({ name: 'Student', email: 'student@example.test', role: 'student' }));
  localStorage.setItem('halo_token', 'mentor-test-token');
  const signal = new AbortController().signal;
  await mentor.openSession(15, { signal });
  assert.equal(captured.method, 'post');
  assert.equal(captured.url, '/student/mentor/open/15');
  assert.equal(captured.headers.Authorization, 'Bearer mentor-test-token');
  assert.equal(captured.signal, signal);
  await assert.rejects(mentor.openSession(undefined), /valid module ID/);
  await assert.rejects(mentor.openSession(0), /valid module ID/);
});


test('protected routes render only for their own role', async () => {
  const React = await import('react');
  const { renderToString } = await import('react-dom/server');
  const { MemoryRouter } = await import('react-router-dom');
  const { AuthContext } = await server.ssrLoadModule('/src/context/login/useAuth.js');
  const { default: Guard } = await server.ssrLoadModule('/src/routes/ProtectedRoute.jsx');
  for (const role of ['student', 'professor', 'admin', 'superadmin']) {
    for (const allowed of ['student', 'professor', 'admin', 'superadmin']) {
      const html = renderToString(React.createElement(MemoryRouter, null,
        React.createElement(AuthContext.Provider, { value: { isAuthenticated: true, role, isLoading: false } },
          React.createElement(Guard, { allowedRoles: [allowed] }, 'protected-content'))));
      assert.equal(html.includes('protected-content'), role === allowed, role + ' accessing ' + allowed);
    }
  }
});

test('student requests preserve backend data and propagate errors without demo fallback', async () => {
  const student = await server.ssrLoadModule('/src/services/student/studentService.js');
  const original = api.defaults.adapter;
  try {
    assert.deepEqual(await student.getSubjectsData(), responseData);
    assert.equal(captured.url, '/student/subjects');
    await student.getSubjectWeeks(81);
    assert.equal(captured.url, '/student/subjects/81/weeks');
    await student.getWeekLesson(92);
    assert.equal(captured.url, '/student/ai-learning-modules/week/92');
    const signal = new AbortController().signal;
    await mentor.sendMessage(73, 'Explain this module', { signal });
    assert.equal(captured.signal, signal);
    api.defaults.adapter = async () => { throw new Error('Backend unavailable'); };
    await assert.rejects(student.getSubjectsData(), /Backend unavailable/);
    await assert.rejects(mentor.sendMessage(73, 'Question'), /Backend unavailable/);
  } finally { api.defaults.adapter = original; }
});


test('lesson and mentor requests have finite timeouts and authenticated transport', async () => {
  const student = await server.ssrLoadModule('/src/services/student/studentService.js');
  localStorage.setItem('halo_user', JSON.stringify({ name: 'Student', email: 's@example.test', role: 'student' }));
  localStorage.setItem('halo_token', 'timeout-test-token');
  await student.getWeekLesson(7);
  assert.equal(captured.timeout, 15000);
  assert.equal(captured.headers.Authorization, 'Bearer timeout-test-token');
  await mentor.openSession(92);
  assert.equal(captured.timeout, 90000);
  await mentor.sendMessage(45, 'Explain');
  assert.equal(captured.timeout, 90000);
});


test('shared timeout bounds ordinary requests without shortening uploads', async () => {
  assert.equal(api.defaults.timeout, 15000);
  await auth.login('account@example.test', 'test-password');
  assert.equal(captured.timeout, 15000);
  await professor.createLearningModule(7, { lessonText: 'Test', files: [] });
  assert.equal(captured.timeout, 180000);
});


test('temporary-password account cannot render normal privileged content', async () => {
  const React = await import('react'); const { renderToString } = await import('react-dom/server');
  const { MemoryRouter } = await import('react-router-dom');
  const { AuthContext } = await server.ssrLoadModule('/src/context/login/useAuth.js');
  const { default: Guard } = await server.ssrLoadModule('/src/routes/ProtectedRoute.jsx');
  for (const path of ['/superadmin', '/superadmin/profile']) {
    const html = renderToString(React.createElement(MemoryRouter, { initialEntries: [path] },
      React.createElement(AuthContext.Provider, { value: { role: 'superadmin', isAuthenticated: true, user: { mustChangePassword: true } } },
        React.createElement(Guard, { allowedRoles: ['superadmin'] }, 'protected-content'))));
    assert.equal(html.includes('protected-content'), path.endsWith('/profile'));
  }
});

test('API diagnostics do not expose token, password or raw error/config', async () => {
  const original = console.warn; const logs = [];
  console.warn = (...args) => logs.push(args);
  try {
    await assert.rejects(api.interceptors.response.handlers[0].rejected({
      code: 'ECONNABORTED', config: { method: 'post', url: '/super-admin/create-admin',
        headers: { Authorization: 'Bearer private-test-token' }, data: { password: 'private-test-password' } },
    }));
    assert.ok(!JSON.stringify(logs).includes('private-test'));
  } finally { console.warn = original; }
});


test('activity widget 401/500 does not invalidate current session', async () => {
 localStorage.setItem('halo_user', JSON.stringify({ name: 'Test', email: 'test@example.test', role: 'superadmin' }));
 localStorage.setItem('halo_token', 'widget-token');
 let events = 0; const listener = () => events++;
 window.addEventListener('halo:session-expired', listener);
 try {
  for (const status of [401, 500]) {
   await assert.rejects(api.interceptors.response.handlers[0].rejected({ response: { status, data: { code: 'AUTHENTICATION_REQUIRED' } }, config: { headers: { Authorization: 'Bearer widget-token' } } }));
   assert.equal(localStorage.getItem('halo_token'), 'widget-token');
  }
  assert.equal(events, 0);
 } finally { window.removeEventListener('halo:session-expired', listener); }
});

test('dashboard renders each successful section independently of the failed section', async () => {
 const React = await import('react'); const { renderToString } = await import('react-dom/server');
 const { MemoryRouter } = await import('react-router-dom');
 const { AuthContext } = await server.ssrLoadModule('/src/context/login/useAuth.js');
 const { DashboardSections } = await server.ssrLoadModule('/src/pages/superadmin/components/SuperAdminDashboard.jsx');
 const render = (statistics, logs) => renderToString(React.createElement(MemoryRouter, null,
  React.createElement(AuthContext.Provider, { value: { user: null } }, React.createElement(DashboardSections, { statistics, logs }))));
 const ok = { isLoading: false, error: null, reload: () => {} };
 const stats = { ...ok, data: { totalUsers: 123, totalAdmins: 4, totalStudents: 100, totalProfessors: 18, activeUsers: 120, inactiveUsers: 3 } };
 const logs = { ...ok, data: [{ id: 1, userName: 'Actor', action: 'Test activity', createdAt: '2026-01-01T12:00:00' }] };
 let html = render(stats, { ...logs, error: new Error('failed') });
 assert.ok(html.includes('Total Users')); assert.ok(html.includes('123')); assert.ok(html.includes('Retry activity logs'));
 html = render({ ...stats, data: null, error: new Error('failed') }, logs);
 assert.ok(html.includes('Test activity')); assert.ok(html.includes('Retry statistics'));
});


test('Super Admin mutations normalize input and send an explicit desired status', async () => {
 const service = await server.ssrLoadModule('/src/services/superadmin/superadminService.js');
 const previous = api.defaults.adapter;
 const requests = [];
 api.defaults.adapter = async config => { requests.push(config); return { data: {}, status: 200, headers: {}, config }; };
 try {
  await service.addAdmin({ name: '  Admin  ', email: ' TEST@EXAMPLE.TEST ', password: 'test-password-12' });
  assert.deepEqual(JSON.parse(requests[0].data), { name: 'Admin', email: 'test@example.test', password: 'test-password-12' });
  await service.setAdminStatus(7, 'ACTIVE'); await service.setAdminStatus(7, 'ACTIVE');
  assert.deepEqual(requests.slice(1).map(c => JSON.parse(c.data)), [{ status: 'ACTIVE' }, { status: 'ACTIVE' }]);
  assert.equal(requests[1].url, '/super-admin/admin/7/status');
  assert.equal(requests.length, 3);
 } finally { api.defaults.adapter = previous; }
});

test('Super Admin monitoring forwards pagination/filter and dashboard only requests ten recent logs', async () => {
 const service = await server.ssrLoadModule('/src/services/superadmin/superadminService.js');
 const previous = api.defaults.adapter;
 const requests = [];
 const rows = [{ id: 1, action: 'Created Admin' }];
 api.defaults.adapter = async config => { requests.push(config); return { data: { content: rows, totalElements: 100 }, status: 200, headers: {}, config }; };
 try {
  const signal = new AbortController().signal;
  const page = await service.getActivityLogs({ signal, params: { page: 2, size: 20, search: 'created', activityType: 'ACCOUNT' } });
  assert.equal(page.totalElements, 100);
  assert.deepEqual(requests[0].params, { page: 2, size: 20, search: 'created', activityType: 'ACCOUNT' });
  assert.equal(requests[0].signal, signal);
  assert.deepEqual(await service.getRecentActivityLogs(), rows);
  assert.deepEqual(requests[1].params, { page: 0, size: 10 });
 } finally { api.defaults.adapter = previous; }
});

test('Super Admin errors display safe specific messages without exposing arbitrary internal text', async () => {
 const { apiErrorMessage } = await server.ssrLoadModule('/src/utils/apiErrors.js');
 assert.equal(apiErrorMessage({ response: { data: { code: 'EMAIL_ALREADY_EXISTS', message: 'private SQL' } } }), 'An account with this email already exists.');
 assert.equal(apiErrorMessage({ response: { data: { message: 'private SQL' } } }), 'The request failed. Please try again.');
});
