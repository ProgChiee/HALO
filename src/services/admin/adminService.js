// Centralized service for all Admin-role data.
//
// TODO: swap the mock returns below for real API calls once your backend
// is ready. Every function already returns a Promise, so the components
// calling these won't need to change shape-wise — only this file will.

import {
  mockAdminStats,
  mockSubjectEnrollment,
  mockAdminRecentActivity,
} from '../../data/admin/adminDashboardData';
import { mockProfessors } from '../../data/admin/professorsData';
import { mockManagedStudents } from '../../data/admin/studentsData';
import { mockAdminProfile } from '../../data/admin/adminProfileData';

function delay(ms = 400) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export async function getAdminDashboardData() {
  await delay();
  // TODO: const res = await apiClient.get('/admin/dashboard'); return res.data;
  return {
    stats: mockAdminStats,
    subjectEnrollment: mockSubjectEnrollment,
    recentActivity: mockAdminRecentActivity,
  };
}

// In-memory copy so Create/Edit/Deactivate feel real during the demo, even
// without a backend yet. Resets on page refresh — that's expected for mock data.
let professorsStore = [...mockProfessors];

export async function getProfessors() {
  await delay();
  // TODO: const res = await apiClient.get('/admin/professors'); return res.data;
  return professorsStore;
}

export async function createProfessor({ fullName, email, professorId }) {
  await delay();
  // TODO: const res = await apiClient.post('/admin/professors', { fullName, email, professorId });
  // return res.data;
  const newProfessor = {
    id: Date.now(),
    fullName,
    email,
    professorId,
    subjectsCount: 0,
    lessonsCount: 0,
    lastLogin: 'Never',
    status: 'active',
  };
  professorsStore = [...professorsStore, newProfessor];
  return newProfessor;
}

export async function toggleProfessorStatus(professorId) {
  await delay(200);
  // TODO: await apiClient.patch(`/admin/professors/${professorId}/toggle-status`);
  professorsStore = professorsStore.map((p) =>
    p.id === professorId ? { ...p, status: p.status === 'active' ? 'inactive' : 'active' } : p
  );
  return { success: true };
}

export async function updateProfessor(professorId, updates) {
  await delay();
  // TODO: const res = await apiClient.patch(`/admin/professors/${professorId}`, updates);
  // return res.data;
  professorsStore = professorsStore.map((p) =>
    p.id === professorId ? { ...p, ...updates } : p
  );
  return professorsStore.find((p) => p.id === professorId);
}

// In-memory copy so Deactivate/Activate feel real during the demo, even
// without a backend yet. Resets on page refresh — that's expected for mock data.
let studentsStore = [...mockManagedStudents];

export async function getStudents() {
  await delay();
  // TODO: const res = await apiClient.get('/admin/students'); return res.data;
  return studentsStore;
}

export async function toggleStudentStatus(studentId) {
  await delay(200);
  // TODO: await apiClient.patch(`/admin/students/${studentId}/toggle-status`);
  studentsStore = studentsStore.map((s) =>
    s.id === studentId ? { ...s, status: s.status === 'active' ? 'inactive' : 'active' } : s
  );
  return { success: true };
}

// In-memory copy so editing feels real during the demo, even without a
// backend yet. Resets on page refresh — that's expected for mock data.
let adminProfileStore = { ...mockAdminProfile };

export async function getAdminProfile() {
  await delay();
  // TODO: const res = await apiClient.get('/admin/profile'); return res.data;
  return adminProfileStore;
}

export async function updateAdminProfile(updates) {
  await delay();
  // TODO: const res = await apiClient.patch('/admin/profile', updates); return res.data;
  adminProfileStore = { ...adminProfileStore, ...updates };
  return adminProfileStore;
}