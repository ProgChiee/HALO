// Centralized service for all Student-role data (Dashboard, Subjects,
// Progress, Badges, Profile).
//
// TODO: swap the mock returns below for real API calls once your backend
// is ready (endpoints suggested in each function's comment). Every
// function already returns a Promise, so the components calling these
// won't need to change shape-wise — only this file will.

import {
  mockStats,
  mockContinueLearning,
  mockEnrolledSubjects,
} from '../../data/student/studentDashboardData';
import {
  mockOverallProgress,
  mockProgressStats,
  mockCompletedSubjects,
  mockQuizScores,
} from '../../data/student/progressData';
import { mockEarnedBadges, mockLockedBadges } from '../../data/student/badgesData';
import { mockProfile } from '../../data/student/profileData';
import { getCatalog } from '../shared/subjectsStore';
import { isWeekCompleted, markWeekComplete } from '../shared/progressStore';

function delay(ms = 400) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export async function getDashboardData() {
  await delay();
  // TODO: const res = await axios.get('/api/student/dashboard'); return res.data;
  const moduleProgress = await getOverallModuleProgress();
  return {
    stats: mockStats,
    continueLearning: mockContinueLearning,
    enrolledSubjects: mockEnrolledSubjects,
    moduleProgress,
  };
}

// Computes the Sidebar's "Module progress" widget from REAL data (the
// shared catalog + shared progress store), instead of the static mock.
// Used across every student page so the widget actually reflects
// quizzes the student has passed (see markLessonComplete()), rather
// than always showing the same hardcoded 37%.
export async function getOverallModuleProgress() {
  await delay(150);
  const catalog = getCatalog();

  let totalWeeks = 0;
  let doneWeeks = 0;
  catalog.forEach((subject) => {
    totalWeeks += subject.weeks.length;
    subject.weeks.forEach((week) => {
      if (isWeekCompleted(subject.id, week.id)) doneWeeks += 1;
    });
  });

  const percent = totalWeeks === 0 ? 0 : Math.round((doneWeeks / totalWeeks) * 100);

  return {
    label: 'Module progress',
    percent,
    detail: `${doneWeeks} of ${totalWeeks} lessons done`,
  };
}

// --- Subjects page: derived from the SHARED catalog (services/shared/
// subjectsStore.js) — same store Professor's Subject Management reads
// and writes — combined with the SHARED progress store (services/shared/
// progressStore.js) that tracks which weeks are actually marked complete.
//
// A week's status is computed relative to the other weeks in its subject:
//   - 'done'   → this week has been marked complete
//   - 'now'    → this is the first NOT-done week (i.e. every week before
//                 it is done) — the one the student should work on next
//   - 'locked' → there's an earlier not-done week before this one

function deriveWeekStatus(subjectId, weeks, weekIndex) {
  const weekId = weeks[weekIndex].id;
  if (isWeekCompleted(subjectId, weekId)) return 'done';

  const firstIncompleteIndex = weeks.findIndex((w) => !isWeekCompleted(subjectId, w.id));
  return weekIndex === firstIncompleteIndex ? 'now' : 'locked';
}

function deriveTopicStatus(subjectId, weeks) {
  if (weeks.length === 0) return 'available';
  const allDone = weeks.every((w) => isWeekCompleted(subjectId, w.id));
  return allDone ? 'completed' : 'available';
}

function transformCatalogToSubjectGroups(catalog) {
  const years = [...new Set(catalog.map((s) => s.year))];

  return years.map((year, idx) => {
    const subjectsForYear = catalog.filter((s) => s.year === year);

    return {
      id: `y${idx}`,
      year,
      subjectName: 'Learning Hub',
      topics: subjectsForYear.map((subject) => {
        const weeksWithStatus = subject.weeks.map((week, i) => ({
          id: week.id,
          label: week.title,
          duration: '30mins',
          status: deriveWeekStatus(subject.id, subject.weeks, i),
        }));
        const currentWeekNumber = weeksWithStatus.findIndex((w) => w.status === 'now') + 1;

        return {
          id: subject.id,
          title: subject.title,
          lessonsCount: subject.weeks.length,
          status: deriveTopicStatus(subject.id, subject.weeks),
          currentWeek: currentWeekNumber || subject.weeks.length, // 0 weeks left → show last week number, or 0 if none
          weeks: weeksWithStatus,
        };
      }),
    };
  });
}

export async function getSubjectsData() {
  await delay();
  const catalog = getCatalog();
  return {
    yearFilters: ['All Years', '1st Year', '2nd Year'],
    overview: {
      topicsDone: catalog.filter((s) => deriveTopicStatus(s.id, s.weeks) === 'completed').length,
      topicsTotal: catalog.length,
    },
    subjectGroups: transformCatalogToSubjectGroups(catalog),
  };
}

export async function getTopicAndWeek(topicId, weekId) {
  await delay(150);
  const catalog = getCatalog();
  const subject = catalog.find((s) => s.id === topicId);
  if (!subject) return { topic: null, week: null };

  const weekIndex = subject.weeks.findIndex((w) => w.id === weekId);
  const week = subject.weeks[weekIndex];
  if (!week) return { topic: null, week: null };

  return {
    topic: { id: subject.id, title: subject.title, lessonsCount: subject.weeks.length },
    week: {
      id: week.id,
      label: week.title,
      duration: '30mins',
      status: deriveWeekStatus(subject.id, subject.weeks, weekIndex),
    },
  };
}

export async function getCurrentTopicAndWeek() {
  await delay(150);
  const catalog = getCatalog();

  for (const subject of catalog) {
    if (subject.weeks.length === 0) continue;
    const firstIncompleteIndex = subject.weeks.findIndex((w) => !isWeekCompleted(subject.id, w.id));
    if (firstIncompleteIndex === -1) continue; // subject fully completed, check next

    const week = subject.weeks[firstIncompleteIndex];
    return {
      topic: { id: subject.id, title: subject.title, lessonsCount: subject.weeks.length },
      week: { id: week.id, label: week.title, duration: '30mins', status: 'now' },
    };
  }

  return { topic: null, week: null };
}

// Marks a week complete in the shared progress store. TODO: once real
// per-student accounts + backend exist, this becomes:
// await apiClient.post(`/student/progress`, { subjectId, weekId });
export async function markLessonComplete(subjectId, weekId) {
  await delay(200);
  markWeekComplete(subjectId, weekId);
  return { success: true };
}

export async function getProgressData() {
  await delay();
  // TODO: const res = await axios.get('/api/student/progress'); return res.data;
  return {
    overall: mockOverallProgress,
    stats: mockProgressStats,
    completedSubjects: mockCompletedSubjects,
    quizScores: mockQuizScores,
  };
}

export async function getBadgesData() {
  await delay();
  // TODO: const res = await axios.get('/api/student/badges'); return res.data;
  return { earned: mockEarnedBadges, locked: mockLockedBadges };
}

export async function getProfileData() {
  await delay();
  // TODO: const res = await axios.get('/api/student/profile'); return res.data;
  return mockProfile;
}