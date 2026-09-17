// Centralized service for all Admin-role data.
//
// Wired to the real backend (com.ptc.halo.controller.AdminController).
// Subject/Week management lives here (not in professorService.js) because
// that's where the backend actually put it.

import apiClient from '../apiClient';

// ── Professors ──────────────────────────────────────────────────────────

export async function getProfessors() {
  const res = await apiClient.get('/admin/professors');
  return res.data;
}

export async function getProfessorById(id) {
  const res = await apiClient.get(`/admin/professors/${id}`);
  return res.data;
}

// NOTE: backend's ProfessorRequest also requires a password field, which
// the current "add professor" form doesn't collect — decide with your
// backend team whether the admin sets an initial password here, or the
// backend should auto-generate one / send a setup email instead.
export async function createProfessor({ name, email, password, professorId }) {
  const res = await apiClient.post('/admin/create-professor', {
    name,
    email,
    password,
    professorId,
  });
  return res.data;
}

export async function updateProfessor(id, { name, email, professorId }) {
  const res = await apiClient.put(`/admin/professors/${id}`, { name, email, professorId });
  return res.data;
}

// No request body — backend flips the status server-side.
export async function toggleProfessorStatus(id) {
  const res = await apiClient.patch(`/admin/professors/${id}/status`);
  return res.data;
}

// ── Students ─────────────────────────────────────────────────────────────

export async function getStudents() {
  const res = await apiClient.get('/admin/students');
  return res.data;
}

export async function getStudentById(id) {
  const res = await apiClient.get(`/admin/students/${id}`);
  return res.data;
}

export async function toggleStudentStatus(id) {
  const res = await apiClient.patch(`/admin/students/${id}/status`);
  return res.data;
}

export async function updateStudent(id, { name, studentId, section, yearLevel }) {
  const res = await apiClient.put(`/admin/students/${id}`, {
    name,
    studentId,
    section,
    yearLevel,
  });
  return res.data;
}

// ── Subjects ─────────────────────────────────────────────────────────────
// Moved here from professorService.js — the backend's subject/week CRUD
// lives entirely under AdminController, professors don't manage their own
// subjects directly on this backend.

// NOTE: backend's SubjectRequest needs subjectCode, subjectName,
// description, and yearLevel — the current "create subject" UI only
// collects a title. The form needs extra fields before this can be wired
// up for real.
export async function createSubject({ subjectCode, subjectName, description, yearLevel }) {
  const res = await apiClient.post('/admin/subjects', {
    subjectCode,
    subjectName,
    description,
    yearLevel,
  });
  return res.data;
}

export async function getSubjects() {
  const res = await apiClient.get('/admin/subjects');
  return res.data;
}

export async function getSubjectById(id) {
  const res = await apiClient.get(`/admin/subjects/${id}`);
  return res.data;
}

export async function updateSubject(id, { subjectCode, subjectName, description, yearLevel }) {
  const res = await apiClient.put(`/admin/subjects/${id}`, {
    subjectCode,
    subjectName,
    description,
    yearLevel,
  });
  return res.data;
}

export async function deleteSubject(id) {
  const res = await apiClient.delete(`/admin/subjects/${id}`);
  return res.data;
}

// ── Weeks ────────────────────────────────────────────────────────────────

// NOTE: backend's WeekRequest needs a weekNumber (Integer) in addition to
// title — the current "add week" UI only collects a title.
export async function addWeek(subjectId, { weekNumber, title }) {
  const res = await apiClient.post(`/admin/subjects/${subjectId}/weeks`, { weekNumber, title });
  return res.data;
}

export async function getWeeks(subjectId) {
  const res = await apiClient.get(`/admin/subjects/${subjectId}/weeks`);
  return res.data;
}

export async function getWeekById(id) {
  const res = await apiClient.get(`/admin/weeks/${id}`);
  return res.data;
}

export async function updateWeek(id, { weekNumber, title }) {
  const res = await apiClient.put(`/admin/weeks/${id}`, { weekNumber, title });
  return res.data;
}

export async function deleteWeek(id) {
  const res = await apiClient.delete(`/admin/weeks/${id}`);
  return res.data;
}

// ✅ Confirmed working — new AdminDashboardController.
// Returns: { totalStudents, totalProfessors, activeUsers, inactiveUsers, totalSubjects, totalModules }
export async function getAdminDashboardData() {
  const res = await apiClient.get('/admin/dashboard');
  return res.data;
}

// ✅ Confirmed working — same controller.
// Returns: [{ id, userName, email, role, activityType, action, createdAt }]
export async function getAdminRecentActivity() {
  const res = await apiClient.get('/admin/dashboard/recent-activity');
  return res.data;
}

// ✅ Confirmed working — new AdminProfessorMonitoringController.
// Returns: [{ userId, professorId, name, email, status, moduleActivities, lastActivity }]
export async function getProfessorMonitoring() {
  const res = await apiClient.get('/admin/professors/monitoring');
  return res.data;
}

// ✅ Confirmed working — new AdminStudentMonitoringController.
// Returns: [{ userId, studentId, name, email, section, yearLevel, status,
// completedModules, passedAssessments, totalBadges, latestAssessmentScore }]
// This finally gives real quiz/assessment-related numbers per student —
// could replace the old getQuizPerformanceData()/getAiUsageData() cards
// on the Monitoring page with real aggregates computed from this list.
export async function getStudentMonitoring() {
  const res = await apiClient.get('/admin/students/monitoring');
  return res.data;
}

// ✅ Confirmed working — added to AdminController.
// Returns: { userId, name, email, role, status }
export async function getAdminProfile() {
  const res = await apiClient.get('/admin/profile');
  return res.data;
}

export async function updateAdminProfile(updates) {
  const res = await apiClient.patch('/admin/profile', updates);
  return res.data;
}

// ✅ Confirmed working — AdminController's new /admin/activity-logs.
// role: optional Role filter ('ADMIN' | 'PROFESSOR' | 'STUDENT' | 'SUPER_ADMIN')
// activityType: optional ActivityType filter — check the ActivityType enum
// on the backend for valid values.
// Returns: [{ id, userName, userEmail, userRole, activityType, action, createdAt }]
export async function getActivityLog({ role, activityType } = {}) {
  const res = await apiClient.get('/admin/activity-logs', {
    params: { role, type: activityType },
  });
  return res.data;
}

export async function getAiUsageData() {
  const res = await apiClient.get('/admin/ai-usage');
  return res.data;
}

export async function getQuizPerformanceData() {
  const res = await apiClient.get('/admin/quiz-performance');
  return res.data;
}

export async function getSessionMonitoringData() {
  const res = await apiClient.get('/admin/login-activity');
  return res.data;
}