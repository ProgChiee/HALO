// API routes verified against the controllers in HALO.zip.
import defaultClient from '../apiClient';

export function createProfessorService(apiClient = defaultClient) {

async function createSubject({ subjectCode, subjectName, description, yearLevel }) {
  const res = await apiClient.post('/professor/subjects', { subjectCode, subjectName, description, yearLevel });
  return res.data;
}

async function getSubjects(config = {}) {
  const res = await apiClient.get('/professor/subjects', config);
  return res.data;
}

async function getSubjectById(id) {
  const res = await apiClient.get(`/professor/subjects/${id}`);
  return res.data;
}

async function updateSubject(id, { subjectCode, subjectName, description, yearLevel }) {
  const res = await apiClient.put(`/professor/subjects/${id}`, { subjectCode, subjectName, description, yearLevel });
  return res.data;
}

async function deleteSubject(id) {
  const res = await apiClient.delete(`/professor/subjects/${id}`);
  return res.data;
}

async function addWeek(subjectId, { weekNumber, title }) {
  const res = await apiClient.post(`/professor/subjects/${subjectId}/weeks`, { weekNumber, title });
  return res.data;
}

async function getWeeks(subjectId, config = {}) {
  const res = await apiClient.get(`/professor/subjects/${subjectId}/weeks`, config);
  return res.data;
}

async function getWeekById(id) {
  const res = await apiClient.get(`/professor/weeks/${id}`);
  return res.data;
}

async function updateWeek(id, { weekNumber, title }) {
  const res = await apiClient.put(`/professor/weeks/${id}`, { weekNumber, title });
  return res.data;
}

async function deleteWeek(id) {
  const res = await apiClient.delete(`/professor/weeks/${id}`);
  return res.data;
}

// The updated controller returns 204 only when this week has no module.
async function getLearningModuleByWeek(weekId, config = {}) {
  const res = await apiClient.get(`/professor/ai-learning-modules/week/${weekId}`, config);
  return res.status === 204 ? null : res.data;
}

async function createLearningModule(weekId, { lessonText, youtubeLink, aiNotes, files }) {
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

async function generateLesson(moduleId) {
  const res = await apiClient.post(`/professor/ai-learning-modules/${moduleId}/generate`);
  return res.data;
}

async function approveLesson(moduleId) {
  const res = await apiClient.put(`/professor/ai-learning-modules/${moduleId}/approve`, undefined, { timeout: 180000 });
  return res.data;
}

async function declineLesson(moduleId) {
  const res = await apiClient.put(`/professor/ai-learning-modules/${moduleId}/decline`);
  return res.data;
}

async function updateLearningModule(moduleId, { lessonText, youtubeLink, aiNotes }) {
  const res = await apiClient.put(`/professor/ai-learning-modules/${moduleId}`, {
    lessonText,
    youtubeLink,
    aiNotes,
  });
  return res.data;
}

async function uploadModuleFile(moduleId, file) {
  const formData = new FormData();
  formData.append('file', file);

  const res = await apiClient.post(`/professor/ai-learning-modules/${moduleId}/files`, formData);
  return res.data;
}

async function deleteModuleFile(fileId) {
  const res = await apiClient.delete(`/professor/ai-learning-modules/files/${fileId}`);
  return res.data;
}

async function getProfessorDashboardData(config = {}) {
  const res = await apiClient.get('/professor/dashboard', config);
  return res.data;
}

async function getStudents(config = {}) {
  const res = await apiClient.get('/professor/students', config);
  return res.data;
}

async function getStudentProgress(userId, config = {}) {
  const res = await apiClient.get(`/professor/students/${userId}/progress`, config);
  return res.data;
}

async function getStudentSubjects(userId) {
  const res = await apiClient.get(`/professor/students/${userId}/subjects`);
  return res.data;
}

async function getProfessorProfile(config = {}) {
  const res = await apiClient.get('/professor/profile', config);
  return res.data;
}

async function getLearningEngagement(page = 0, size = 20, config = {}) {
  const { data } = await apiClient.get('/professor/students/engagement', { ...config, params: { page, size } });
  return data;
}

async function getStudentProgressSummaries(page = 0, size = 20, config = {}) {
  const { data } = await apiClient.get('/professor/students/progress-summaries', {
    ...config, params: { page, size },
  });
  return data;
}

  return { createSubject, getSubjects, getSubjectById, updateSubject, deleteSubject, addWeek, getWeeks, getWeekById, updateWeek, deleteWeek, getLearningModuleByWeek, createLearningModule, generateLesson, approveLesson, declineLesson, updateLearningModule, uploadModuleFile, deleteModuleFile, getProfessorDashboardData, getStudents, getStudentProgress, getStudentSubjects, getProfessorProfile, getStudentProgressSummaries, getLearningEngagement };
}

export const { createSubject, getSubjects, getSubjectById, updateSubject, deleteSubject, addWeek, getWeeks, getWeekById, updateWeek, deleteWeek, getLearningModuleByWeek, createLearningModule, generateLesson, approveLesson, declineLesson, updateLearningModule, uploadModuleFile, deleteModuleFile, getProfessorDashboardData, getStudents, getStudentProgress, getStudentSubjects, getProfessorProfile, getStudentProgressSummaries, getLearningEngagement } = createProfessorService();
