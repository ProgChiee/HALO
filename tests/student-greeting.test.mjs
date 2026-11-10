import { test } from 'node:test';
import assert from 'node:assert/strict';
import { studentGreeting } from '../src/utils/studentGreeting.js';
import { parseLoginResponse } from '../src/utils/backendContract.js';
import { readSession, writeSession } from '../src/utils/session.js';
const response = { name: 'Archie Student', email: 'synthetic@example.test', role: 'STUDENT', token: 'synthetic-token' };
const storage = () => { const map = new Map(); return { getItem: key => map.get(key) ?? null, setItem: (key, value) => map.set(key, value), removeItem: key => map.delete(key) }; };
for (const [firstLogin, title, subtitle] of [
  [true, 'Welcome to HALO, Archie! 👋', "Let's get started with your learning journey."],
  [false, 'Welcome back, Archie! 👋', "Here's your learning overview for today."],
  [null, 'Welcome, Archie! 👋', "Here's your learning overview for today."],
]) test(`server classification ${firstLogin} survives refresh, tabs and restoration`, () => {
  const session = parseLoginResponse({ ...response, firstLogin }); const stores = [storage(), storage()];
  writeSession(session.user, session.token, true, stores);
  for (let i = 0; i < 3; i++) assert.deepEqual(studentGreeting(readSession(stores).user, {}), { title, subtitle });
  const otherDevice = parseLoginResponse({ ...response, firstLogin: false });
  assert.match(studentGreeting(otherDevice.user, {}).title, /Welcome back/);
  assert.equal(studentGreeting(readSession(stores).user, {}).title, title);
});
test('old sessions and malformed classifications are neutral; other roles ignore the field', () => {
  for (const firstLogin of [undefined, 'true', 1]) assert.match(studentGreeting(parseLoginResponse({ ...response, firstLogin }).user, {}).title, /^Welcome,/);
  assert.equal(parseLoginResponse({ ...response, role: 'ADMIN', firstLogin: true }).user.firstLogin, undefined);
});
test('Preview and Support use target identity without changing the normal session', () => {
  const user = Object.freeze({ name: 'Administrator', firstLogin: true });
  for (const preview of [true, false]) assert.equal(studentGreeting(user, { acting: true, preview, target: { name: 'Target Student' } }).title, 'Student Dashboard — Target Student');
  assert.equal(user.firstLogin, true);
});
test('loading is neutral and missing/whitespace names use safe fallback', () => {
  assert.deepEqual(studentGreeting(null, {}, true), { title: 'Student Dashboard', subtitle: '' });
  for (const name of [undefined, '', '   ']) assert.equal(studentGreeting({ name, firstLogin: true }, {}).title, 'Welcome to HALO, there! 👋');
  assert.equal(studentGreeting({ name: '  Maria   Santos ', firstLogin: false }, {}).title, 'Welcome back, Maria! 👋');
});
