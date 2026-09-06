// Centralized service for all Super Admin-role data (Dashboard, Admins
// management, Profile).
//
// TODO: swap the mock logic below for real API calls once your backend is
// ready (suggested endpoints in each function's comment). Every function
// already returns a Promise, so the components calling these won't need
// to change shape-wise — only this file will.

import { mockPlatformStats, mockRecentActivity } from '../../data/superadmin/superadminDashboardData';
import { mockAdmins } from '../../data/superadmin/adminsData';
import { mockSuperAdminProfile } from '../../data/superadmin/superadminProfileData';

function delay(ms = 400) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export async function getSuperAdminDashboardData() {
  await delay();
  // TODO: const res = await apiClient.get('/superadmin/dashboard'); return res.data;

  // "Total Admins" is the one stat we can actually compute live from
  // in-memory data (the Admins CRUD store below) — the rest (total users,
  // students, subjects, etc.) would need real cross-role backend
  // aggregation, so those stay as static mock numbers for now.
  const stats = mockPlatformStats.map((stat) =>
    stat.id === 'admins' ? { ...stat, value: String(adminsStore.length) } : stat
  );

  return { stats, recentActivity: mockRecentActivity };
}

// In-memory copy so Add/Delete feel real during the demo, even without a
// backend yet. Resets on page refresh — that's expected for mock data.
let adminsStore = [...mockAdmins];

export async function getAdmins() {
  await delay();
  // TODO: const res = await apiClient.get('/superadmin/admins'); return res.data;
  return adminsStore;
}

export async function addAdmin({ fullName, email }) {
  await delay();
  // TODO: const res = await apiClient.post('/superadmin/admins', { fullName, email });
  // return res.data;
  const newAdmin = {
    id: Date.now(),
    fullName,
    email,
    dateAdded: new Date().toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' }),
    status: 'active',
  };
  adminsStore = [...adminsStore, newAdmin];
  return newAdmin;
}

export async function deleteAdmin(adminId) {
  await delay();
  // TODO: await apiClient.delete(`/superadmin/admins/${adminId}`);
  adminsStore = adminsStore.filter((a) => a.id !== adminId);
  return { success: true };
}

export async function toggleAdminStatus(adminId) {
  await delay(200);
  // TODO: const res = await apiClient.patch(`/superadmin/admins/${adminId}/toggle-status`); return res.data;
  adminsStore = adminsStore.map((a) =>
    a.id === adminId ? { ...a, status: a.status === 'active' ? 'inactive' : 'active' } : a
  );
  return adminsStore.find((a) => a.id === adminId);
}

export async function getSuperAdminProfile() {
  await delay();
  // TODO: const res = await apiClient.get('/superadmin/profile'); return res.data;
  return mockSuperAdminProfile;
}