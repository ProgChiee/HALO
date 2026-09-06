// Centralized service for all Professor-role data.
//
// TODO: swap the mock returns below for real API calls once your backend
// is ready. Every function already returns a Promise, so the components
// calling these won't need to change shape-wise — only this file will.

import {
  mockProfessorStats,
  mockProfessorSubjects,
} from '../../data/professor/professorDashboardData';
import { mockProfessorProfile } from '../../data/professor/professorProfileData';
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
    content: {
      method: 'text',
      fileName: '',
      fileType: '',
      linkUrl: '',
      text: '',
      sourceFiles: [],
      aiGeneratedText: '',
      aiStatus: 'idle', // 'idle' | 'generating' | 'pending_review' | 'approved'
    },
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

// Canned "AI-generated" lesson drafts, so Regenerate visibly produces a
// DIFFERENT attempt each time (closer to how a real generation model
// would behave) instead of the exact same text every click.
const MOCK_GENERATED_DRAFTS = [
  (fileNames) =>
    `Based on the uploaded materials (${fileNames.join(', ')}), this lesson covers the core concepts step by step, starting with foundational terms before moving into practical application. Key definitions are introduced early, followed by real-world examples drawn directly from the source material. Students are encouraged to relate each concept back to actual hospitality scenarios they may encounter during OJT.`,
  (fileNames) =>
    `Drawing from ${fileNames.join(', ')}, this draft lesson opens with a short overview of the topic, then breaks the material into three digestible sections: background/context, core procedure, and common mistakes to avoid. Each section ends with a quick self-check question to reinforce understanding before the student proceeds to the quiz.`,
  (fileNames) =>
    `This lesson synthesizes the uploaded materials (${fileNames.join(', ')}) into a narrative-style walkthrough — introducing the "why" behind the topic before the "how." It includes callouts for industry best practices and flags a few edge cases that are easy to overlook, based on patterns found in the source documents.`,
];

// Mock AI lesson generation from uploaded source materials (PDFs/images).
// TODO: once the real AI backend is ready, replace with something like:
//   const res = await apiClient.post(`/professor/subjects/${subjectId}/weeks/${weekId}/generate-lesson`,
//     { fileIds: sourceFiles.map(f => f.id) });
//   return res.data.generatedText;
// This mock ignores the actual file CONTENTS (no OCR/parsing here — that's
// backend work) and just uses the file NAMES to make the output feel tied
// to what was uploaded.
export async function generateLessonFromMaterials(sourceFiles) {
  await delay(1500); // longer delay — feels like real AI processing, not a quick CRUD save
  const fileNames = sourceFiles.map((f) => f.name);
  const draftFn = MOCK_GENERATED_DRAFTS[Math.floor(Math.random() * MOCK_GENERATED_DRAFTS.length)];
  return draftFn(fileNames);
}

// In-memory copy so editing feels real during the demo, even without a
// backend yet. Resets on page refresh — that's expected for mock data.
let professorProfileStore = { ...mockProfessorProfile };

export async function getProfessorProfile() {
  await delay();
  // TODO: const res = await apiClient.get('/professor/profile'); return res.data;
  return professorProfileStore;
}

export async function updateProfessorProfile(updates) {
  await delay();
  // TODO: const res = await apiClient.patch('/professor/profile', updates); return res.data;
  professorProfileStore = { ...professorProfileStore, ...updates };
  return professorProfileStore;
}