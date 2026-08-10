// Mock data for the Badges page.
//
// TODO: once the backend is ready, replace the exports below with a real
// API call (e.g. GET /api/students/:id/badges) — Badges.jsx won't need to
// change shape-wise as long as the response keeps this structure.
//
// Badge icons use emoji for a colorful, illustrated look without needing
// custom art assets. Swap `emoji` for an `iconUrl` field later if you'd
// rather use real badge artwork from a designer.

export const mockEarnedBadges = [
  {
    id: 1,
    emoji: '⭐',
    title: 'First Module Completed',
    description: 'Completed your very first module',
  },
  {
    id: 2,
    emoji: '🏅',
    title: 'Perfect Score',
    description: 'Scored 100% on a quiz',
  },
  {
    id: 3,
    emoji: '🔥',
    title: 'Five Modules Completed',
    description: 'Completed 5 modules total',
  },
];

export const mockLockedBadges = [
  {
    id: 4,
    title: 'Ten Modules Completed',
    description: 'Complete 10 modules total',
  },
  {
    id: 5,
    title: 'Quiz Streak',
    description: 'Score 90%+ on 3 quizzes in a row',
  },
  {
    id: 6,
    title: 'Subject Master',
    description: 'Complete every module in one subject',
  },
];