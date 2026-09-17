// Centralized service for Professor-role lesson authoring and academics.
//
// Wired to the real backend (AiLearningModuleController for lessons,
// ProfessorAcademicController for subjects/weeks, ProfessorDashboardController,
// ProfessorStudentController, ProfessorProfileController).
//
// IMPORTANT — lesson authoring is a different workflow than the old mock
// version: professors do NOT edit a week's objectives/content/video
// directly. Instead: upload raw materials for a week (text/YouTube
// link/notes/files) as an "AI Learning Module" → trigger AI generation →
// review the generated objectives/knowledge/examples/summary → approve or
// decline. LessonEditor.jsx was redesigned to match this create →
// generate → approve/decline flow instead of direct field editing.

import apiClient from '../apiClient';

// ── Subjects & Weeks ─────────────────────────────────────────────────────
// ✅ Confirmed working — professors have their OWN subject/week CRUD here
// (ProfessorAcademicController, /api/professor/subjects & /weeks) —
// separate from adminService.js's /api/admin/subjects. Both appear to
// operate on the same shared subjects table (viewAllSubjects() here isn't
// filtered to "subjects this professor created"), so professors can now
// browse/manage the full subject catalog directly — no more depending on
// Admin for this.

export async function createSubject({ subjectCode, subjectName, description, yearLevel }) {
  const res = await apiClient.post('/professor/subjects', { subjectCode, subjectName, description, yearLevel });
  return res.data;
}

export async function getSubjects() {
  const res = await apiClient.get('/professor/subjects');
  return res.data;
}

export async function getSubjectById(id) {
  const res = await apiClient.get(`/professor/subjects/${id}`);
  return res.data;
}

export async function updateSubject(id, { subjectCode, subjectName, description, yearLevel }) {
  const res = await apiClient.put(`/professor/subjects/${id}`, { subjectCode, subjectName, description, yearLevel });
  return res.data;
}

export async function deleteSubject(id) {
  const res = await apiClient.delete(`/professor/subjects/${id}`);
  return res.data;
}

export async function addWeek(subjectId, { weekNumber, title }) {
  const res = await apiClient.post(`/professor/subjects/${subjectId}/weeks`, { weekNumber, title });
  return res.data;
}

export async function getWeeks(subjectId) {
  const res = await apiClient.get(`/professor/subjects/${subjectId}/weeks`);
  return res.data;
}

export async function getWeekById(id) {
  const res = await apiClient.get(`/professor/weeks/${id}`);
  return res.data;
}

export async function updateWeek(id, { weekNumber, title }) {
  const res = await apiClient.put(`/professor/weeks/${id}`, { weekNumber, title });
  return res.data;
}

export async function deleteWeek(id) {
  const res = await apiClient.delete(`/professor/weeks/${id}`);
  return res.data;
}

// ── Lesson authoring (AI Learning Modules) ──────────────────────────────

// files: array of File objects from an <input type="file" multiple />
export async function createLearningModule(weekId, { lessonText, youtubeLink, aiNotes, files }) {
  const formData = new FormData();
  formData.append('weekId', weekId);
  if (lessonText) formData.append('lessonText', lessonText);
  if (youtubeLink) formData.append('youtubeLink', youtubeLink);
  if (aiNotes) formData.append('aiNotes', aiNotes);
  (files ?? []).forEach((file) => formData.append('files', file));

  const res = await apiClient.post('/professor/ai-learning-modules', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return res.data;
}

export async function generateLesson(moduleId) {
  const res = await apiClient.post(`/professor/ai-learning-modules/${moduleId}/generate`);
  return res.data;
}

export async function approveLesson(moduleId) {
  const res = await apiClient.put(`/professor/ai-learning-modules/${moduleId}/approve`);
  return res.data;
}

export async function declineLesson(moduleId) {
  const res = await apiClient.put(`/professor/ai-learning-modules/${moduleId}/decline`);
  return res.data;
}

export async function updateLearningModule(moduleId, { lessonText, youtubeLink, aiNotes }) {
  const res = await apiClient.put(`/professor/ai-learning-modules/${moduleId}`, {
    lessonText,
    youtubeLink,
    aiNotes,
  });
  return res.data;
}

export async function uploadModuleFile(moduleId, file) {
  const formData = new FormData();
  formData.append('file', file);

  const res = await apiClient.post(`/professor/ai-learning-modules/${moduleId}/files`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return res.data;
}

export async function deleteModuleFile(fileId) {
  const res = await apiClient.delete(`/professor/ai-learning-modules/files/${fileId}`);
  return res.data;
}

// ── Dashboard, Students, Profile ────────────────────────────────────────

// Returns: { totalStudents, totalSubjects, totalModules, approvedModules, totalAssessments, totalPassedAttempts }
export async function getProfessorDashboardData() {
  const res = await apiClient.get('/professor/dashboard');
  return res.data;
}

// Returns: [{ userId, studentId, name, email, section, yearLevel }]
export async function getStudents() {
  const res = await apiClient.get('/professor/students');
  return res.data;
}

// Returns: { userId, studentId, name, email, section, completedModules, passedAssessments, totalBadges }
export async function getStudentProgress(userId) {
  const res = await apiClient.get(`/professor/students/${userId}/progress`);
  return res.data;
}

// Returns: [{ subjectId, subjectCode, subjectName, yearLevel, totalWeeks, completedWeeks, progressPercentage }]
export async function getStudentSubjects(userId) {
  const res = await apiClient.get(`/professor/students/${userId}/subjects`);
  return res.data;
}

// Returns: { userId, name, email, role, status, professorId }
export async function getProfessorProfile() {
  const res = await apiClient.get('/professor/profile');
  return res.data;
}

// ⚠️ NOT YET AVAILABLE — only GET /professor/profile exists on the
// backend so far, no update route. Calling this will 404.
export async function updateProfessorProfile(updates) {
  const res = await apiClient.patch('/professor/profile', updates);
  return res.data;
}