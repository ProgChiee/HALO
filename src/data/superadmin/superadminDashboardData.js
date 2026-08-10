// Mock data for the Super Admin Dashboard.
//
// TODO: once the backend is ready, replace with a real API call
// (e.g. GET /api/superadmin/dashboard).

export const mockPlatformStats = [
  { id: 'totalUsers', icon: 'UsersRound', value: '254', label: 'Total Users' },
  { id: 'activeUsers', icon: 'UserCheck', value: '230', label: 'Active Users' },
  { id: 'inactiveUsers', icon: 'UserX', value: '24', label: 'Inactive Users' },
  { id: 'admins', icon: 'Users', value: '4', label: 'Total Admins' },
  { id: 'professors', icon: 'GraduationCap', value: '12', label: 'Total Professors' },
  { id: 'students', icon: 'UserCheck', value: '238', label: 'Total Students' },
  { id: 'subjects', icon: 'Layers', value: '16', label: 'Total Subjects' },
];

export const mockRecentActivity = [
  { id: 1, actor: 'Admin Rosa Santos', action: 'added a new Professor account', time: '2 hours ago' },
  { id: 2, actor: 'Admin Mark Reyes', action: 'created a new Subject: "Front Office Operations"', time: '5 hours ago' },
  { id: 3, actor: 'Prof. Dela Cruz', action: 'published Week 3 module for Kitchen Essentials', time: 'Yesterday' },
  { id: 4, actor: 'Admin Rosa Santos', action: 'updated system email domain settings', time: '2 days ago' },
];