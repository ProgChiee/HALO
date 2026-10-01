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
