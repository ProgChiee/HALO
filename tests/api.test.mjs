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
  await assert.rejects(reject({ response: { status: 401 }, config: { headers: { Authorization: 'Bearer old-token' } } }));
  assert.equal(localStorage.getItem('halo_token'), 'new-token');
  let notified = false;
  window.addEventListener('halo:session-expired', () => { notified = true; }, { once: true });
  await assert.rejects(reject({ response: { status: 401 }, config: { headers: { Authorization: 'Bearer new-token' } } }));
  assert.equal(localStorage.getItem('halo_token'), null);
  assert.equal(notified, true);
});
