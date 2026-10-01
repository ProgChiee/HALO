// Centralized service for the Quiz/Assessment feature.
//
// Wired to the real backend (com.ptc.halo.controller.StudentAssessmentController).
// This is a richer attempt-based flow than "get quiz, submit score" — the
// backend tracks each attempt separately and enforces pass/retry rules.

import apiClient from '../apiClient';

// Check this BEFORE showing the "Start quiz" button — tells you whether an
// assessment exists yet, whether the student already passed, has an
// unfinished attempt to resume, etc.
// Returns: { moduleId, assessmentExists, assessmentAvailable, hasUnfinishedAttempt, alreadyPassed, canTakeAssessment }
export async function getAssessmentStatus(moduleId, config = {}) {
  const res = await apiClient.get(`/student/assessment/status/${moduleId}`, config);
  return res.data;
}

// Returns: { id, title, passingScore, questions: [{ id, questionNumber, questionText, optionA, optionB, optionC, optionD }] }
export async function getAssessment(moduleId, config = {}) {
  const res = await apiClient.get(`/student/assessment/${moduleId}`, config);
  return res.data;
}

// Returns: { attemptId, assessmentId, startedAt }
export async function startAttempt(moduleId) {
  const res = await apiClient.post(`/student/assessment/start/${moduleId}`);
  return res.data;
}

// StudentAnswerRequest wraps { answers: [{ questionId: number, answer: 'A'|'B'|'C'|'D' }] }.
// submit fills attemptId, score and passed only; GET result fills feedback.
export async function submitAttempt(attemptId, answers) {
  const res = await apiClient.post(`/student/assessment/submit/${attemptId}`, { answers });
  return res.data;
}

// Returns: [{ attemptId, score, passed, startedAt, submittedAt }]
export async function getAttemptHistory(moduleId) {
  const res = await apiClient.get(`/student/assessment/attempts/${moduleId}`);
  return res.data;
}

// AssessmentService.getAttemptResult populates the per-question feedback list.
export async function getAttemptResult(attemptId) {
  const res = await apiClient.get(`/student/assessment/result/${attemptId}`);
  return res.data;
}

// NOTE: there is also POST /assessment/generate/{moduleId}, but the
// professor's approveLesson() action already triggers assessment
// generation automatically on the backend — the frontend likely never
// needs to call this directly. Not wrapped here; ask your backend team to
// confirm before wiring up a manual "generate quiz" button anywhere.