// API routes verified against the controllers in HALO.zip.
import defaultClient from '../apiClient';

// Self-only endpoint. Deliberately not included in the acting/preview service factory.
export async function updateAcademicProfile(field, value) {
  const fields = { studentId: 'STUDENT_ID', section: 'SECTION', yearLevel: 'YEAR_LEVEL' };
  if (!Object.hasOwn(fields, field)) throw new Error('Unsupported academic field');
  return (await defaultClient.patch('/student/profile/academic', { field: fields[field], value })).data;
}

// Never included in Admin acting/preview adapters.
export async function enrollInSubject(subjectId) { return (await defaultClient.post('/student/subjects/' + encodeURIComponent(subjectId) + '/enrollment')).data; }
export function createStudentService(apiClient = defaultClient) {

async function getStudyCalendar(month, config = {}) {
  return (await apiClient.get('/student/study-calendar', { ...config, params: month ? { month } : {} })).data;
}

async function getProgressData(config = {}) {
  const res = await apiClient.get('/student/progress', config);
  return res.data;
}

async function getBadgesData(config = {}) {
  const res = await apiClient.get('/student/badges', config);
  return res.data;
}

async function getBadgeCatalog(config = {}) {
  return (await apiClient.get('/student/badges/catalog', config)).data;
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
async function getProfilePhoto(config = {}) { return (await apiClient.get('/student/profile/photo', { ...config, responseType: 'blob' })).data; }
async function getMySubjects(config = {}) { return (await apiClient.get('/student/subjects/my', config)).data; }
async function getBrowseSubjects(config = {}) { return (await apiClient.get('/student/subjects/browse', config)).data; }
async function getSubjectCurriculum(subjectId, config = {}) { return (await apiClient.get(`/student/subjects/${encodeURIComponent(subjectId)}/curriculum`, config)).data; }
return { getSubjectCurriculum, getMySubjects, getBrowseSubjects, getProfilePhoto, getProgressData, getBadgesData, getBadgeCatalog, getWeekLesson, getDashboardData, getSubjectsData, getSubjectWeeks, getProfileData, recordLessonStudy, getStudyCalendar };
}
export const { getProgressData, getBadgesData, getBadgeCatalog, getWeekLesson, getDashboardData, getSubjectsData, getSubjectWeeks, getProfileData, recordLessonStudy, getStudyCalendar } = createStudentService();
