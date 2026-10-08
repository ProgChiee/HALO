// API routes verified against the controllers in HALO.zip.
import apiClient from '../apiClient';

export async function getProfessors(config = {}) {
  const res = await apiClient.get('/admin/professors', config);
  return res.data;
}

export async function getProfessorById(id) {
  const res = await apiClient.get(`/admin/professors/${id}`);
  return res.data;
}

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

export async function setProfessorStatus(id, status) {
  const res = await apiClient.patch(`/admin/professors/${id}/status`, { status });
  return res.data;
}

export async function getStudents(config = {}) {
  const res = await apiClient.get('/admin/students', config);
  return res.data;
}

export async function getStudentById(id) {
  const res = await apiClient.get(`/admin/students/${id}`);
  return res.data;
}

export async function setStudentStatus(id, status) {
  const res = await apiClient.patch(`/admin/students/${id}/status`, { status });
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

export async function getAdminDashboardData(config = {}) {
  const res = await apiClient.get('/admin/dashboard', config);
  return res.data;
}

export async function getAdminRecentActivity(config = {}) {
  const res = await apiClient.get('/admin/dashboard/recent-activity', config);
  return res.data;
}

export async function getProfessorMonitoring(config = {}) {
  const res = await apiClient.get('/admin/professors/monitoring', config);
  return res.data;
}

export async function getStudentMonitoring(config = {}) {
  const res = await apiClient.get('/admin/students/monitoring', config);
  return res.data;
}

export async function getAdminProfile(config = {}) {
  const res = await apiClient.get('/admin/profile', config);
  return res.data;
}

export async function getActivityLog({ role, activityType, signal, params = {} } = {}) {
  const res = await apiClient.get('/admin/activity-logs', {
    params: { page: 0, size: 20, ...params, role, type: activityType ?? params.activityType, activityType: undefined },
    signal,
  });
  return res.data;
}
