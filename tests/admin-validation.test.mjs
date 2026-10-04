import { test } from 'node:test';
import assert from 'node:assert/strict';
import { validateProfessor, professorValidationError } from '../src/utils/adminProfessorValidation.js';

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
  assert.equal(professorValidationError({}), 'Something went wrong. Please try again.');
});
