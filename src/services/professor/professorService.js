// API routes verified against the controllers in HALO.zip.
import apiClient from '../apiClient';

export async function createSubject({ subjectCode, subjectName, description, yearLevel }) {
  const res = await apiClient.post('/professor/subjects', { subjectCode, subjectName, description, yearLevel });
  return res.data;
}

export async function getSubjects(config = {}) {
  const res = await apiClient.get('/professor/subjects', config);
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

export async function getWeeks(subjectId, config = {}) {
  const res = await apiClient.get(`/professor/subjects/${subjectId}/weeks`, config);
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

// The updated controller returns 204 only when this week has no module.
export async function getLearningModuleByWeek(weekId, config = {}) {
  const res = await apiClient.get(`/professor/ai-learning-modules/week/${weekId}`, config);
  return res.status === 204 ? null : res.data;
}

export async function createLearningModule(weekId, { lessonText, youtubeLink, aiNotes, files }) {
  const formData = new FormData();
  formData.append('weekId', weekId);
  if (lessonText) formData.append('lessonText', lessonText);
  if (youtubeLink) formData.append('youtubeLink', youtubeLink);
  if (aiNotes) formData.append('aiNotes', aiNotes);
  (files ?? []).forEach((file) => formData.append('files', file));

  // Let the browser supply the multipart boundary with the Content-Type.
  const res = await apiClient.post('/professor/ai-learning-modules', formData);
  return res.data;
}

export async function generateLesson(moduleId) {
  const res = await apiClient.post(`/professor/ai-learning-modules/${moduleId}/generate`);
  return res.data;
}

export async function approveLesson(moduleId) {
  const res = await apiClient.put(`/professor/ai-learning-modules/${moduleId}/approve`, undefined, { timeout: 180000 });
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

  const res = await apiClient.post(`/professor/ai-learning-modules/${moduleId}/files`, formData);
  return res.data;
}

export async function deleteModuleFile(fileId) {
  const res = await apiClient.delete(`/professor/ai-learning-modules/files/${fileId}`);
  return res.data;
}

export async function getProfessorDashboardData(config = {}) {
  const res = await apiClient.get('/professor/dashboard', config);
  return res.data;
}

export async function getStudents(config = {}) {
  const res = await apiClient.get('/professor/students', config);
  return res.data;
}

export async function getStudentProgress(userId, config = {}) {
  const res = await apiClient.get(`/professor/students/${userId}/progress`, config);
  return res.data;
}

export async function getStudentSubjects(userId) {
  const res = await apiClient.get(`/professor/students/${userId}/subjects`);
  return res.data;
}

export async function getProfessorProfile(config = {}) {
  const res = await apiClient.get('/professor/profile', config);
  return res.data;
}

export async function getStudentProgressSummaries(page = 0, size = 20, config = {}) {
  const { data } = await apiClient.get('/professor/students/progress-summaries', {
    ...config, params: { page, size },
  });
  return data;
}
