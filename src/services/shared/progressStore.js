// Shared in-memory store tracking which weeks the (mock) logged-in
// student has marked complete, keyed by subjectId.
//
// TODO: once there's real backend + real per-student accounts, this
// becomes a proper "student_progress" table (studentId, subjectId,
// weekId, completedAt) — this file goes away, and studentService.js
// calls the API directly instead. The shape here (Set of completed
// weekIds per subject) is intentionally simple so swapping it out later
// doesn't require touching the UI components that call markLessonComplete().

let completedWeeksBySubject = {}; // { [subjectId]: Set<weekId> }

export function getCompletedWeekIds(subjectId) {
  return completedWeeksBySubject[subjectId]
    ? [...completedWeeksBySubject[subjectId]]
    : [];
}

export function isWeekCompleted(subjectId, weekId) {
  return completedWeeksBySubject[subjectId]?.has(weekId) ?? false;
}

export function markWeekComplete(subjectId, weekId) {
  if (!completedWeeksBySubject[subjectId]) {
    completedWeeksBySubject[subjectId] = new Set();
  }
  completedWeeksBySubject[subjectId].add(weekId);
}