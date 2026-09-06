// Mock data for the Admin Monitoring (Activity Log) page.
//
// TODO: once the backend is ready, replace with a real API call
// (e.g. GET /api/admin/activity-log). Suggested shape stays the same -
// each entry needs: id, text, time (display string), type (success|error,
// used for the colored dot), category (used for filtering), and a real
// timestamp field once it's not mock data anymore (for sorting/pagination).

export const mockActivityLog = [
  { id: 1, text: 'New student registered: Gina Ramos', time: '2 min ago', type: 'success', category: 'student' },
  { id: 2, text: 'Earl Santos account deactivated', time: '14 min ago', type: 'error', category: 'student' },
  { id: 3, text: 'Prof. Dela Cruz created subject "Front Office Operations"', time: '32 min ago', type: 'success', category: 'subject' },
  { id: 4, text: 'New professor account created: Prof. Bautista', time: '1 hour ago', type: 'success', category: 'professor' },
  { id: 5, text: 'Prof. Reyes added Module "Week 4: Guest Relations"', time: '1 hour ago', type: 'success', category: 'subject' },
  { id: 6, text: 'Admin Marasigan deactivated Prof. Villanueva', time: '2 hours ago', type: 'error', category: 'professor' },
  { id: 7, text: 'New student registered: Paolo Cruz', time: '3 hours ago', type: 'success', category: 'student' },
  { id: 8, text: 'Subject "Housekeeping Operations" deleted by Prof. Reyes', time: '3 hours ago', type: 'error', category: 'subject' },
  { id: 9, text: 'New admin account created: Marasigan, J.', time: '5 hours ago', type: 'success', category: 'account' },
  { id: 10, text: 'Prof. Bautista updated professor info', time: '6 hours ago', type: 'success', category: 'professor' },
  { id: 11, text: '48 failed login attempts detected (IP throttled)', time: 'Yesterday', type: 'error', category: 'system' },
  { id: 12, text: 'New student registered: Kim Villareal', time: 'Yesterday', type: 'success', category: 'student' },
  { id: 13, text: 'Student Earl Santos account reactivated', time: 'Yesterday', type: 'success', category: 'student' },
  { id: 14, text: 'Prof. Dela Cruz created subject "Kitchen Fundamentals"', time: '2 days ago', type: 'success', category: 'subject' },
  { id: 15, text: 'System backup completed', time: '2 days ago', type: 'success', category: 'system' },
];

export const MONITORING_CATEGORIES = [
  { id: 'all', label: 'All' },
  { id: 'student', label: 'Students' },
  { id: 'professor', label: 'Professors' },
  { id: 'subject', label: 'Subjects' },
  { id: 'account', label: 'Accounts' },
  { id: 'system', label: 'System' },
];