import { test } from 'node:test';
import assert from 'node:assert/strict';
import { validateProfessor, professorValidationError } from '../src/utils/adminProfessorValidation.js';
import { adminErrorMessage } from '../src/utils/adminErrors.js';
import { apiErrorMessage } from '../src/utils/apiErrors.js';

const valid = { name: ' Professor ', email: ' PROF@example.test ', professorId: ' P-1 ', password: 'password-123' };
test('professor forms enforce required, trimmed values and field bounds', () => {
  assert.equal(validateProfessor(valid, true), '');
  assert.equal(validateProfessor({ ...valid, password: undefined }), '');
  for (const field of ['name', 'email', 'professorId', 'password']) {
    assert.ok(validateProfessor({ ...valid, [field]: '   ' }, true));
  }
  for (const [field, value] of [['name', 'a'.repeat(101)], ['email', 'invalid'], ['email', 'a'.repeat(255)], ['professorId', 'a'.repeat(256)]]) {
    assert.ok(validateProfessor({ ...valid, [field]: value }, true));
  }
});
test('password boundary uses UTF-8 bytes and preserves eight-character minimum', () => {
  for (const password of ['short', 'a'.repeat(73), 'é'.repeat(37)]) assert.ok(validateProfessor({ ...valid, password }, true));
  for (const password of ['12345678', 'a'.repeat(72), 'é'.repeat(36)]) assert.equal(validateProfessor({ ...valid, password }, true), '');
});
test('validation errors remain visible without displaying arbitrary server contents', () => {
  const message = professorValidationError({ response: { data: { code: 'VALIDATION_FAILED', message: 'PRIVATE_SQL', errors: { name: 'PRIVATE_VALUE' } } } });
  assert.match(message, /required fields/);
  assert.doesNotMatch(message, /PRIVATE/);
  assert.equal(professorValidationError({}), 'The request failed. Please try again.');
});

test('Admin business errors give safe actionable messages without exposing request or server details', () => {
  for (const [status, code, expected] of [
    [400, 'INVALID_TARGET_ROLE', /required role/],
    [404, 'RESOURCE_NOT_FOUND', /Reload the list/],
    [409, 'EMAIL_ALREADY_EXISTS', /different email/],
    [500, 'ADMIN_REQUEST_FAILED', /try again later/],
    [401, 'AUTHENTICATION_REQUIRED', /sign in/],
    [403, 'ACCESS_DENIED', /permission/],
  ]) {
    const error = { config: { headers: { Authorization: 'SECRET' }, data: { password: 'SECRET' } }, response: { status, data: { code, message: 'SECRET SQL path', errors: { name: 'SECRET' } } } };
    assert.match(adminErrorMessage(error), expected);
    assert.match(professorValidationError(error), expected);
    assert.doesNotMatch(adminErrorMessage(error), /SECRET/);
  }
  assert.match(adminErrorMessage({ response: { status: 409, data: { message: 'SECRET' } } }), /conflicts/);
  assert.match(adminErrorMessage({ code: 'ERR_NETWORK' }), /connection/);
  assert.match(apiErrorMessage({ response: { data: { code: 'INCORRECT_CURRENT_PASSWORD' } } }), /current password is incorrect/);
});

test('Professor ID conflict uses a fixed actionable message', async () => {
  const { adminErrorMessage } = await import('../src/utils/adminErrors.js');
  assert.equal(adminErrorMessage({ response: { status: 409, data: { code: 'PROFESSOR_ID_CONFLICT', message: 'private SQL' } } }),
    'This Professor ID is already in use. Enter a different Professor ID.');
});
