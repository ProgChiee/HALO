// Mock data for the Admin "Login / Session Monitoring" tab.
//
// TODO: once the backend is ready, this should come from the auth service
// itself — e.g. GET /api/admin/login-activity for the event list, and
// GET /api/admin/active-sessions for the live count (session store /
// JWT expiry tracking on the backend, not something the frontend can
// compute on its own).

export const mockSessionSummary = {
  activeSessionsNow: 47,
  loginsToday: 213,
  failedAttemptsToday: 9,
};

export const mockActiveSessionsByRole = [
  { role: 'Student', count: 38 },
  { role: 'Professor', count: 7 },
  { role: 'Admin', count: 2 },
  { role: 'Superadmin', count: 0 },
];

export const mockLoginActivity = [
  { id: 1, name: 'Gina Ramos', role: 'Student', time: '3 min ago', status: 'success' },
  { id: 2, name: 'Prof. Dela Cruz', role: 'Professor', time: '12 min ago', status: 'success' },
  { id: 3, name: 'unknown@example.com', role: '—', time: '18 min ago', status: 'failed' },
  { id: 4, name: 'Archie Talidong', role: 'Student', time: '24 min ago', status: 'success' },
  { id: 5, name: 'Admin Marasigan', role: 'Admin', time: '41 min ago', status: 'success' },
  { id: 6, name: 'unknown@example.com', role: '—', time: '44 min ago', status: 'failed' },
  { id: 7, name: 'unknown@example.com', role: '—', time: '44 min ago', status: 'failed' },
  { id: 8, name: 'Kim Villareal', role: 'Student', time: '1 hour ago', status: 'success' },
  { id: 9, name: 'Prof. Bautista', role: 'Professor', time: '2 hours ago', status: 'success' },
  { id: 10, name: 'Paolo Cruz', role: 'Student', time: '3 hours ago', status: 'success' },
  { id: 11, name: 'Earl Santos', role: 'Student', time: '5 hours ago', status: 'failed' },
  { id: 12, name: 'Prof. Villanueva', role: 'Professor', time: 'Yesterday', status: 'success' },
];