// Mock data for the Professor's Student Progress page.
//
// TODO: once the backend is ready, replace with a real API call
// (e.g. GET /api/professor/student-progress).

export const mockProgressOverviewStats = [
  { id: 'classAvg', icon: 'TrendingUp', value: '72%', label: 'Class Average', sublabel: 'Overall progress' },
  { id: 'passRate', icon: 'CheckCircle2', value: '91%', label: 'Quiz Pass Rate', sublabel: 'Pass / Fail ratio' },
  { id: 'totalStudents', icon: 'GraduationCap', value: '7', label: 'Total Students', sublabel: 'Enrolled students' },
];

export const mockStudentProgressList = [
  {
    id: 1,
    name: 'Archie Talidong',
    completedModules: '3/5',
    quizAvgScore: 85,
    passCount: 2,
    failCount: 1,
    progressPercent: 68,
  },
  {
    id: 2,
    name: 'Gina Ramos',
    completedModules: '4/5',
    quizAvgScore: 92,
    passCount: 4,
    failCount: 0,
    progressPercent: 80,
  },
  {
    id: 3,
    name: 'Earl Santos',
    completedModules: '1/5',
    quizAvgScore: 60,
    passCount: 1,
    failCount: 2,
    progressPercent: 20,
  },
];