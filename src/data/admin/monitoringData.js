// Categories for the Admin Monitoring (Activity Log) page filter chips —
// matches the backend's ActivityType enum exactly (AUTH, ACCOUNT, MODULE,
// ASSESSMENT, PROGRESS, BADGE). The log entries themselves now come from
// the real GET /admin/activity-logs endpoint (see adminService.js).

export const MONITORING_CATEGORIES = [
  { id: 'all', label: 'All' },
  { id: 'AUTH', label: 'Auth' },
  { id: 'ACCOUNT', label: 'Accounts' },
  { id: 'MODULE', label: 'Modules' },
  { id: 'ASSESSMENT', label: 'Assessments' },
  { id: 'PROGRESS', label: 'Progress' },
  { id: 'BADGE', label: 'Badges' },
];