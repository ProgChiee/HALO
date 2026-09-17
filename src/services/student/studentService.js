// Centralized service for Student-role data.
//
// Wired to the real backend. Confirmed against StudentProgressController,
// StudentBadgeController, and StudentAiLearningController.

import apiClient from '../apiClient';

// Returns one row per module: { moduleId, weekId, completed, completedAt }
// — a flat list, not a single "overall %" summary. Compute any
// percent/label you need for the UI from this list on the frontend.
export async function getProgressData() {
  const res = await apiClient.get('/student/progress');
  return res.data;
}

export async function getBadgesData() {
  const res = await apiClient.get('/student/badges');
  return res.data;
}

// Only the WEEK's approved lesson (no separate "topic"/subject lookup
// needed here — the backend keys directly off weekId). This 404s if the
// lesson for that week hasn't been approved yet by the professor.
export async function getWeekLesson(weekId) {
  const res = await apiClient.get(`/student/ai-learning-modules/week/${weekId}`);
  return res.data;
}

// ✅ Confirmed working — StudentDashboardController.
// Returns: { completedModules, totalBadges, passedAssessments, latestAssessmentScore }
export async function getDashboardData() {
  const res = await apiClient.get('/student/dashboard');
  return res.data;
}

// ✅ Confirmed working — StudentLearningProgressionController.
// Returns: [{ subjectId, subjectCode, subjectName, yearLevel, totalWeeks, completedWeeks, progressPercentage }]
export async function getSubjectsData() {
  const res = await apiClient.get('/student/subjects');
  return res.data;
}

// ✅ Confirmed working — same controller as above.
// Returns: [{ weekId, weekNumber, title, moduleId, unlocked, completed, lessonAvailable }]
// `unlocked`/`lessonAvailable` are the keys to gate the UI — e.g. show a
// lock icon and disable "Start lesson" when unlocked is false.
export async function getSubjectWeeks(subjectId) {
  const res = await apiClient.get(`/student/subjects/${subjectId}/weeks`);
  return res.data;
}

// ── ⚠️ STILL NOT AVAILABLE on the backend ──────────────────────────────
// No "current week" shortcut endpoint yet. Flagged (not deleted) so
// components don't crash on a missing import.
//
// Also still worth asking: is there ever a manual "mark lesson complete"
// action, or does StudentModuleProgressResponse's `completed` flag get set
// automatically server-side when a student passes the module's
// assessment? If it's automatic, the frontend likely never needs to call
// anything like markLessonComplete() at all.

export async function getOverallModuleProgress() {
  const res = await apiClient.get('/student/module-progress');
  return res.data;
}

export async function getCurrentTopicAndWeek() {
  const res = await apiClient.get('/student/current-week');
  return res.data;
}

// ✅ Confirmed working — new StudentProfileController.
// Returns: { userId, name, email, role, status, studentId, section, yearLevel }
export async function getProfileData() {
  const res = await apiClient.get('/student/profile');
  return res.data;
}