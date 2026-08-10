// Mock data for the Student Dashboard.
// Once the backend is ready, replace the exports below with real API calls
// (e.g. inside a useEffect + fetch/axios in StudentDashboard.jsx) — the
// component itself won't need to change shape-wise if you keep the same
// field names.

export const mockStats = [
  { id: 'subjects', icon: 'BookOpen', value: '16', label: 'Enrolled subjects' },
  { id: 'progress', icon: 'TrendingUp', value: '23%', label: 'Overall progress' },
  { id: 'badges', icon: 'Award', value: '3/8', label: 'Earned badges' },
  { id: 'modules', icon: 'CheckCircle2', value: '18/80', label: 'Module completed' },
];

export const mockContinueLearning = {
  title: 'Kitchen Essentials and Basic Food Preparation',
  meta: 'Week 4 · Guest Relations',
  lastAccessedLabel: 'Last accessed',
};

export const mockEnrolledSubjects = [
  { id: 1, name: 'Introduction to Quick Food Service', progress: 100 },
  { id: 2, name: 'Kitchen Essentials and Basic Food Preparation', progress: 60 },
  { id: 3, name: 'Macro Perspective to Tourism and Hospitality', progress: 40 },
];

export const mockModuleProgress = {
  label: 'Module 1 progress',
  percent: 37,
  detail: '3 of 8 lessons done',
};