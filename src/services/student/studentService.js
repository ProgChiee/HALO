// API routes verified against the controllers in HALO.zip.
import defaultClient from '../apiClient';

export function createStudentService(apiClient = defaultClient) {

async function getProgressData(config = {}) {
  const res = await apiClient.get('/student/progress', config);
  return res.data;
}

async function getBadgesData(config = {}) {
  const res = await apiClient.get('/student/badges', config);
  return res.data;
}

async function recordLessonStudy(weekId, config = {}) {
  await apiClient.post(`/student/ai-learning-modules/week/${weekId}/study`, {}, config);
}

async function getWeekLesson(weekId, config = {}) {
  const res = await apiClient.get(`/student/ai-learning-modules/week/${weekId}`, { timeout: 15000, ...config });
  return res.data;
}

async function getDashboardData(config = {}) {
  const res = await apiClient.get('/student/dashboard', config);
  return res.data;
}

async function getSubjectsData(config = {}) {
  const res = await apiClient.get('/student/subjects', config);
  return res.data;
}

async function getSubjectWeeks(subjectId, config = {}) {
  const res = await apiClient.get(`/student/subjects/${subjectId}/weeks`, config);
  return res.data;
}

async function getProfileData(config = {}) {
  const res = await apiClient.get('/student/profile', config);
  return res.data;
}
return { getProgressData, getBadgesData, getWeekLesson, getDashboardData, getSubjectsData, getSubjectWeeks, getProfileData, recordLessonStudy };
}
export const { getProgressData, getBadgesData, getWeekLesson, getDashboardData, getSubjectsData, getSubjectWeeks, getProfileData, recordLessonStudy } = createStudentService();
