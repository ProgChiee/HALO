import { test } from 'node:test';
import assert from 'node:assert/strict';
import { selectContinueLesson } from '../src/utils/continueLearning.js';
const group = (id, extra = {}) => ({ subject: { subjectId: id }, weeks: [{ weekId: id, moduleId: id,
  weekNumber: 1, lessonAvailable: true, unlocked: true, completed: false, ...extra }] });
test('recent activity wins across subjects regardless of subject order', () => {
  const recent = group(42, { lastActivityAt: '2026-10-26T12:30:00' });
  assert.equal(selectContinueLesson([group(1), recent]).week.moduleId, 42);
  assert.equal(selectContinueLesson([recent, group(1)]).week.moduleId, 42);
});
test('completed recent module remains resumable; incomplete wins equal timestamps', () => {
  const completed = group(42, { completed: true, lastActivityAt: '2026-10-26T12:30:00' });
  assert.equal(selectContinueLesson([group(1), completed]).week.moduleId, 42);
  assert.equal(selectContinueLesson([completed, group(1, { lastActivityAt: '2026-10-26T12:30:00.000' })]).week.moduleId, 1);
});
test('locked, unpublished and missing modules cannot be resumed', () => {
  for (const excluded of [{ unlocked: false }, { lessonAvailable: false }, { moduleId: null }]) {
    assert.equal(selectContinueLesson([group(42, { lastActivityAt: '2026-10-26T12:30:00', ...excluded }), group(1)]).week.moduleId, 1);
  }
});
test('no history uses deterministic incomplete fallback, not completed modules', () => {
  assert.equal(selectContinueLesson([group(42), group(1)]).week.moduleId, 1);
  assert.equal(selectContinueLesson([group(1, { completed: true })]), null);
  assert.equal(selectContinueLesson([]), null);
});
