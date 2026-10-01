// Centralized service for Super Admin-role data.
//
// Wired to the real backend (com.ptc.halo.controller.SuperAdminController).
// NOTE the base path is "/super-admin" (hyphenated), not "/superadmin".
//
// NOTE: two different response shapes exist here — addAdmin() (create)
// returns AdminResponse ({ name, email, role } only, no id/status), while
// getAdmins()/getAdminById()/updateAdmin()/toggleAdminStatus() all return
// AdminListResponse, which DOES include { id, status }. Use the list
// endpoint's data for anything that needs to target a specific admin.

import apiClient from '../apiClient';

export async function getAdmins(config = {}) {
  const res = await apiClient.get('/super-admin/admins', config);
  return res.data;
}

export async function getAdminById(id) {
  const res = await apiClient.get(`/super-admin/admin/${id}`); // singular "admin", not "admins"
  return res.data;
}

export async function addAdmin({ name, email, password }) {
  const res = await apiClient.post('/super-admin/create-admin', { name, email, password });
  return res.data;
}

export async function updateAdmin(id, { name, email }) {
  const res = await apiClient.put(`/super-admin/admin/${id}`, { name, email });
  return res.data;
}

// No request body — backend flips the status server-side.
export async function toggleAdminStatus(id) {
  const res = await apiClient.patch(`/super-admin/admin/${id}/status`);
  return res.data;
}

// ⚠️ There is no DELETE endpoint for admins on the backend — only status
// toggling (active/inactive/blocked). If you need a true "remove this
// admin" action, ask your backend team to add
// DELETE /api/super-admin/admin/{id}; otherwise use toggleAdminStatus()
// to deactivate instead of removing.

export async function getSuperAdminDashboardData(config = {}) {
  const res = await apiClient.get('/super-admin/dashboard', config);
  return res.data;
}

export async function getUserReports(config = {}) {
  const res = await apiClient.get('/super-admin/reports/users', config);
  return res.data;
}

export async function getActivityLogs(config = {}) {
  const res = await apiClient.get('/super-admin/activity-logs', config);
  return res.data;
}

// ✅ Confirmed working — added to SuperAdminController.
// Returns: { userId, name, email, role, status }
export async function getSuperAdminProfile(config = {}) {
  const res = await apiClient.get('/super-admin/profile', config);
  return res.data;
}
export async function getAdminMonitoring(config = {}) {
  const res = await apiClient.get('/super-admin/admins/monitoring', config);
  return res.data;
}
