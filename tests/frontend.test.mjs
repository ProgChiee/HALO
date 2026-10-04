import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import path from 'node:path';
import { parseLoginResponse, findAvailableWeek, buildAssessmentAnswers } from '../src/utils/backendContract.js';
import { readSession } from '../src/utils/session.js';
import { mapWithConcurrency } from '../src/utils/asyncPool.js';

const contract = JSON.parse(readFileSync(new URL('./fixtures/backend-contract.json', import.meta.url)));
const walk = (directory) => readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
  const file = path.join(directory, entry.name);
  return entry.isDirectory() ? walk(file) : [file];
});
const normalize = (url) => url.replace(/\$\{[^}]+\}|\{[^}]+\}/g, ':id');

test('every service URL and HTTP verb exists in the supplied Java controllers', () => {
  let count = 0;
  for (const file of walk('src/services').filter((file) => file.endsWith('.js'))) {
    const source = readFileSync(file, 'utf8');
    for (const match of source.matchAll(/apiClient\.(get|post|put|patch|delete)\(['"`]([^'"`]+)['"`]/g)) {
      count++;
      const [, method, url] = match;
      assert.ok(contract.endpoints.some((endpoint) => endpoint.method === method.toUpperCase()
        && normalize(endpoint.path) === normalize('/api' + url)), `${file}: ${method} ${url}`);
    }
  }
  assert.ok(count > 50, 'the scan must cover all service layers');
});

test('login validates the flat response and maps all four backend roles', () => {
  for (const [apiRole, expected] of Object.entries({ ADMIN: 'admin', SUPER_ADMIN: 'superadmin', PROFESSOR: 'professor', STUDENT: 'student' })) {
    const session = parseLoginResponse({ name: 'Test', email: 'test@example.test', role: apiRole, token: 'test-token' });
    assert.equal(session.user.role, expected);
  }
  assert.throws(() => parseLoginResponse({ name: 'Test', email: 'test@example.test', role: 'ADMIN' }));
  assert.throws(() => parseLoginResponse({ name: 'Test', email: 'test@example.test', role: 'OWNER', token: 'test' }));
});

test('restoration requires a user and token from the same storage', () => {
  const user = JSON.stringify({ name: 'Test', email: 'test@example.test', role: 'student' });
  const storage = (values) => ({ getItem: (key) => values[key] ?? null });
  assert.equal(readSession([storage({ halo_user: user }), storage({ halo_token: 'test' })]).user, null);
  assert.equal(readSession([storage({ halo_user: '{bad', halo_token: 'old' }), storage({ halo_user: user, halo_token: 'valid' })]).token, 'valid');
  assert.equal(readSession([storage({ halo_user: user, halo_token: '' })]).user, null);
});

test('next lesson ignores unpublished, completed and locked weeks and sorts by week number', () => {
  const weeks = [
    { weekId: 4, weekNumber: 4, unlocked: true, lessonAvailable: true, completed: false },
    { weekId: 1, weekNumber: 1, unlocked: true, lessonAvailable: false, completed: false },
    { weekId: 3, weekNumber: 3, unlocked: true, lessonAvailable: true, completed: false },
    { weekId: 2, weekNumber: 2, unlocked: false, lessonAvailable: true, completed: false },
    { weekId: 0, weekNumber: 0, unlocked: true, lessonAvailable: true, completed: true },
  ];
  assert.equal(findAvailableWeek(weeks).weekId, 3);
  assert.equal(weeks[0].weekId, 4, 'input should not be mutated');
  assert.equal(findAvailableWeek([weeks[1], weeks[3], weeks[4]]), undefined);
});

test('assessment payload uses numeric question IDs and valid letters for every question', () => {
  const questions = [{ id: 41 }, { id: 42 }];
  assert.deepEqual(buildAssessmentAnswers(questions, { 41: 'A', 42: 'D' }), [
    { questionId: 41, answer: 'A' }, { questionId: 42, answer: 'D' },
  ]);
  assert.throws(() => buildAssessmentAnswers(questions, { 41: 'A' }));
  assert.throws(() => buildAssessmentAnswers(questions, { 41: 0, 42: 'D' }));
  assert.throws(() => buildAssessmentAnswers([], {}));
  assert.equal(contract.dtos.StudentAnswerRequest.answers, 'List<AnswerItem>');
});

test('progress requests have bounded concurrency, preserve order and stop on abort', async () => {
  let active = 0;
  let peak = 0;
  const result = await mapWithConcurrency([1, 2, 3, 4, 5, 6], 2, async (item) => {
    peak = Math.max(peak, ++active);
    await new Promise((resolve) => setTimeout(resolve, 5));
    active--;
    return item * 2;
  });
  assert.equal(peak, 2);
  assert.deepEqual(result, [2, 4, 6, 8, 10, 12]);
  const controller = new AbortController();
  controller.abort();
  await assert.rejects(mapWithConcurrency([1], 2, () => assert.fail('must not start'), controller.signal));
});

test('all statically referenced CSS module classes exist', () => {
  for (const file of walk('src').filter((file) => file.endsWith('.jsx'))) {
    const source = readFileSync(file, 'utf8');
    const cssImport = source.match(/import styles from ['"]([^'"]+)['"]/);
    if (!cssImport) continue;
    const css = readFileSync(path.resolve(path.dirname(file), cssImport[1]), 'utf8');
    for (const [, selector] of source.matchAll(/styles\.([A-Za-z_]\w*)/g)) {
      assert.match(css, new RegExp('\\.' + selector + '(?![\\w-])'), `${file}: ${selector}`);
    }
  }
});


test('lesson errors distinguish access denial from AI provider and authentication failures', async () => {
  const { lessonErrorMessage } = await import('../src/utils/lessonErrors.js');
  assert.match(lessonErrorMessage({ response: { status: 403, data: { code: 'STUDENT_NOT_ENROLLED' } } }), /year level/);
  assert.match(lessonErrorMessage({ response: { status: 401 } }), /sign in/);
  assert.match(lessonErrorMessage({ response: { status: 502 } }), /still available/);
  assert.match(lessonErrorMessage({ response: { status: 405, data: { code: 'METHOD_NOT_ALLOWED' } } }), /method/);
});


test('all roles survive remembered and tab-only refresh without an ADMIN fallback', async () => {
  const { writeSession } = await import('../src/utils/session.js');
  const { ROLE_HOME } = await import('../src/utils/roles.js');
  const storage = () => { const data = new Map(); return { getItem: k => data.get(k) ?? null, setItem: (k,v) => data.set(k,v), removeItem: k => data.delete(k) }; };
  for (const role of ['ADMIN', 'STUDENT', 'PROFESSOR', 'SUPER_ADMIN']) {
    for (const remember of [true, false]) {
      const stores = [storage(), storage()];
      const session = parseLoginResponse({ name: 'Account', email: 'admin@example.test', role, token: 'test-token' });
      writeSession(session.user, session.token, remember, stores);
      assert.equal(JSON.parse(stores[remember ? 0 : 1].getItem('halo_user')).role, role);
      assert.equal(readSession(stores).user.role, session.user.role);
      assert.equal(ROLE_HOME[readSession(stores).user.role], '/' + session.user.role);
      assert.equal(stores[remember ? 1 : 0].getItem('halo_token'), null);
    }
  }
  assert.throws(() => parseLoginResponse({ name: 'Test', email: 'a', token: 'test', role: 'toString' }));
  assert.throws(() => parseLoginResponse({ name: 'Test', email: 'a', token: 'test', role: 'ORGANIZER' }));
});


test('lesson is displayed while mentor initialization is still pending', async () => {
  const { loadLessonChat } = await import('../src/utils/lessonLoader.js');
  const events = [];
  let finish;
  const pending = new Promise(resolve => { finish = resolve; });
  const controller = new AbortController();
  const task = loadLessonChat({ weekId: '7', signal: controller.signal,
    getLesson: async () => ({ id: 92, weekId: 7, status: 'APPROVED', aiGenerationStatus: 'COMPLETED' }),
    openMentor: async id => { assert.equal(id, 92); return pending; },
    onLesson: () => events.push('lesson'), onMentor: () => events.push('mentor'), onError: assert.fail });
  await new Promise(resolve => setImmediate(resolve));
  assert.deepEqual(events, ['lesson']);
  finish({ moduleId: 92, sessionId: 45, messages: [] });
  await task;
  assert.deepEqual(events, ['lesson', 'mentor']);
});

test('invalid, unpublished, forbidden, offline and timed-out lessons reach an error state', async () => {
  const { loadLessonChat } = await import('../src/utils/lessonLoader.js');
  const { lessonErrorMessage } = await import('../src/utils/lessonErrors.js');
  for (const [weekId, result, error, expected] of [
    ['undefined', null, null, /not found/],
    ['7', { id: 92, weekId: 7, status: 'PENDING' }, null, /published/],
    ['7', null, { response: { status: 403 } }, /access/],
    ['7', null, { response: { status: 401 } }, /sign in/],
    ['7', null, { code: 'ERR_NETWORK' }, /Backend unavailable/],
    ['7', null, { code: 'ECONNABORTED' }, /timed out/],
    ['7', { id: 92, weekId: 8 }, null, /mismatched/],
  ]) {
    let reported = false;
    await loadLessonChat({ weekId, signal: new AbortController().signal,
      getLesson: async () => { if(error) throw error; return result; },
      openMentor: () => assert.fail('must not open'), onLesson: assert.fail, onMentor: assert.fail,
      onError: (failure, stage) => { reported = true; assert.equal(stage, 'lesson'); assert.match(lessonErrorMessage(failure, stage), expected); } });
    assert.ok(reported);
  }
});

test('mentor failure retains lesson; switching modules suppresses stale session callbacks', async () => {
  const { loadLessonChat } = await import('../src/utils/lessonLoader.js');
  for (const abort of [false, true]) {
    const controller = new AbortController(); const events = [];
    await loadLessonChat({ weekId: 7, signal: controller.signal,
      getLesson: async () => ({ id: 92, weekId: 7, status: 'APPROVED', aiGenerationStatus: 'COMPLETED' }),
      onLesson: () => events.push('lesson'),
      openMentor: async () => { if(abort) controller.abort(); throw { code: 'ECONNABORTED' }; },
      onMentor: assert.fail, onError: (_, stage) => events.push(stage + '-error') });
    assert.deepEqual(events, abort ? ['lesson'] : ['lesson', 'mentor-error']);
  }
});


test('temporary password flag survives storage and timeout errors are safe', async () => {
  const { apiErrorMessage } = await import('../src/utils/apiErrors.js');
  const result = parseLoginResponse({ role: 'SUPER_ADMIN', name: 'Test', email: 'test@example.test', token: 'test-token', mustChangePassword: true });
  const values = { halo_user: JSON.stringify(result.user), halo_token: result.token };
  assert.equal(readSession([{ getItem: key => values[key] }]).user.mustChangePassword, true);
  assert.equal(apiErrorMessage({ code: 'ECONNABORTED', config: { password: 'never shown' } }), 'The request took too long. Please try again.');
});
