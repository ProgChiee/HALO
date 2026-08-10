// Mock data for the Progress page.
//
// TODO: once the backend is ready, replace the exports below with a real
// API call (e.g. GET /api/students/:id/progress) — Progress.jsx won't need
// to change shape-wise as long as the response keeps this structure.

export const mockOverallProgress = 23; // percentage, shown as the big "23%"

export const mockProgressStats = [
  { id: 'subjects', icon: 'BookOpen', value: '1/16', label: 'Completed Subjects' },
  { id: 'modules', icon: 'CheckCircle2', value: '18/80', label: 'Completed Modules' },
  { id: 'quizAvg', icon: 'Star', value: '85%', label: 'Quiz Scores (avg)' },
  { id: 'badges', icon: 'Award', value: '3/8', label: 'Total Badges' },
];

export const mockCompletedSubjects = [
  { id: 1, name: 'Introduction to Quick Food Service', progress: 100 },
  { id: 2, name: 'Kitchen Essentials and Basic Food Preparation', progress: 60 },
  { id: 3, name: 'Macro Perspective to Tourism and Hospitality', progress: 40 },
];

export const mockQuizScores = [
  { id: 1, title: 'Introduction to Quick Food Service · Week 1', score: 92, grade: 'A' },
  { id: 2, title: 'Food & Beverage Services · Week 2', score: 85, grade: 'B+' },
];