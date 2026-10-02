// API routes verified against the controllers in HALO.zip.
import apiClient from '../apiClient';

export async function getProgressData(config = {}) {
  const res = await apiClient.get('/student/progress', config);
  return res.data;
}

export async function getBadgesData(config = {}) {
  const res = await apiClient.get('/student/badges', config);
  return res.data;
}

export async function getWeekLesson(weekId, config = {}) {
  const res = await apiClient.get(`/student/ai-learning-modules/week/${weekId}`, { timeout: 15000, ...config });
  return res.data;
}

export async function getDashboardData(config = {}) {
  const res = await apiClient.get('/student/dashboard', config);
  return res.data;
}

export async function getSubjectsData(config = {}) {
  const res = await apiClient.get('/student/subjects', config);
  return res.data;
}

export async function getSubjectWeeks(subjectId, config = {}) {
  const res = await apiClient.get(`/student/subjects/${subjectId}/weeks`, config);
  return res.data;
}

export async function getProfileData(config = {}) {
  const res = await apiClient.get('/student/profile', config);
  return res.data;
}