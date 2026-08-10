// Mock data for the Admin Dashboard.
//
// TODO: once the backend is ready, replace with a real API call
// (e.g. GET /api/admin/dashboard).

export const mockAdminStats = [
  { id: 'students', icon: 'Users', value: '256', label: 'Total Students' },
  { id: 'professors', icon: 'GraduationCap', value: '18', label: 'Total Professors' },
  { id: 'subjects', icon: 'BookOpen', value: '34', label: 'Total Subjects' },
  { id: 'modules', icon: 'FileText', value: '170', label: 'Total Modules' },
];

export const mockSubjectEnrollment = [
  { id: 1, name: 'Introduction to Quick Food Service', students: 48, percent: 100 },
  { id: 2, name: 'Front Office Operations', students: 41, percent: 85 },
  { id: 3, name: 'Food and Beverage Services', students: 39, percent: 81 },
  { id: 4, name: 'Housekeeping Operations', students: 35, percent: 73 },
];

export const mockAdminRecentActivity = [
  { id: 1, text: 'New student registered: Gina Ramos', time: '2 min ago', type: 'success' },
  { id: 2, text: 'New student registered: Gina Ramos', time: '2 min ago', type: 'success' },
  { id: 3, text: 'Earl Santos account deactivated', time: '2 min ago', type: 'error' },
];