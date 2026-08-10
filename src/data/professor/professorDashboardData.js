// Mock data for the Professor Dashboard.
//
// TODO: once the backend is ready, replace with a real API call
// (e.g. GET /api/professor/dashboard).

export const mockProfessorStats = [
  { id: 'subjects', icon: 'BookOpen', value: '3', label: 'Total Subjects' },
  { id: 'modules', icon: 'FileText', value: '15', label: 'Total Modules' },
  { id: 'students', icon: 'Users', value: '123', label: 'Total Students' },
];

export const mockProfessorSubjects = [
  { id: 1, name: 'Front Office Operations', modules: 5, students: 41, status: 'active' },
  { id: 2, name: 'Housekeeping Operations', modules: 5, students: 38, status: 'active' },
  { id: 3, name: 'Food and Beverage Services', modules: 5, students: 44, status: 'active' },
];