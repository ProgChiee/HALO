// Centralized service for all Professor-role data.
//
// TODO: swap the mock returns below for real API calls once your backend
// is ready. Every function already returns a Promise, so the components
// calling these won't need to change shape-wise — only this file will.

import {
  mockProfessorStats,
  mockProfessorSubjects,
} from '../../data/professor/professorDashboardData';
import {
  mockProgressOverviewStats,
  mockStudentProgressList,
} from '../../data/professor/studentProgressData';
import { getCatalog, setCatalog } from '../shared/subjectsStore';

function delay(ms = 400) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export async function getProfessorDashboardData() {
  await delay();
  // TODO: const res = await apiClient.get('/professor/dashboard'); return res.data;
  return {
    stats: mockProfessorStats,
    subjects: mockProfessorSubjects,
  };
}

export async function getStudentProgressData() {
  await delay();
  // TODO: const res = await apiClient.get('/professor/student-progress'); return res.data;
  return {
    stats: mockProgressOverviewStats,
    students: mockStudentProgressList,
  };
}

// All functions below read/write through getCatalog()/setCatalog() from
// the SHARED store (services/shared/subjectsStore.js) — not a private
// local copy. This is what makes these changes visible to the Student
// side too (studentService.js reads from the same shared store).

export async function getManagedSubjects() {
  await delay();
  // TODO: const res = await apiClient.get('/professor/subjects'); return res.data;
  return getCatalog();
}

export async function createSubject(title, year = '1st Year') {
  await delay();
  // TODO: const res = await apiClient.post('/professor/subjects', { title, year }); return res.data;
  const newSubject = { id: `s${Date.now()}`, title, year, weeks: [] };
  setCatalog([...getCatalog(), newSubject]);
  return newSubject;
}

export async function updateSubject(subjectId, title) {
  await delay();
  // TODO: await apiClient.patch(`/professor/subjects/${subjectId}`, { title });
  setCatalog(getCatalog().map((s) => (s.id === subjectId ? { ...s, title } : s)));
  return { success: true };
}

export async function deleteSubject(subjectId) {
  await delay();
  // TODO: await apiClient.delete(`/professor/subjects/${subjectId}`);
  setCatalog(getCatalog().filter((s) => s.id !== subjectId));
  return { success: true };
}

export async function addWeek(subjectId, title) {
  await delay();
  // TODO: const res = await apiClient.post(`/professor/subjects/${subjectId}/weeks`, { title });
  const newWeek = {
    id: `w${Date.now()}`,
    title,
    objectives: [],
    content: { method: 'text', fileName: '', fileType: '', linkUrl: '', text: '' },
    video: { title: '', source: '', duration: '', url: '' },
  };
  setCatalog(
    getCatalog().map((s) =>
      s.id === subjectId ? { ...s, weeks: [...s.weeks, newWeek] } : s
    )
  );
  return newWeek;
}

export async function getSubjectAndWeek(subjectId, weekId) {
  await delay(150);
  // TODO: const res = await apiClient.get(`/professor/subjects/${subjectId}/weeks/${weekId}`); return res.data;
  const subject = getCatalog().find((s) => s.id === subjectId);
  const week = subject?.weeks.find((w) => w.id === weekId);
  return { subject: subject ?? null, week: week ?? null };
}

// updates is a partial week object, e.g. { objectives: [...] } or
// { content: {...} } or { video: {...} } — merges into the existing week.
export async function updateWeekDetails(subjectId, weekId, updates) {
  await delay();
  // TODO: const res = await apiClient.patch(
  //   `/professor/subjects/${subjectId}/weeks/${weekId}`, updates
  // ); return res.data;
  setCatalog(
    getCatalog().map((s) => {
      if (s.id !== subjectId) return s;
      return {
        ...s,
        weeks: s.weeks.map((w) => (w.id === weekId ? { ...w, ...updates } : w)),
      };
    })
  );
  return { success: true };
}