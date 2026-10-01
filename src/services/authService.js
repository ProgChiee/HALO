// Centralized authentication service.
//
// Wired to the real Spring Boot backend (com.ptc.halo). Endpoints confirmed
// against the actual AuthController.java.

import apiClient from './apiClient';

export async function login(email, password) {
  const res = await apiClient.post('/auth/login', { email, password }, { skipAuth: true });
  // Backend's LoginResponse is FLAT: { name, email, token, role }
  // (no nested "user" object). Callers should read res.token, res.role, etc.
  // directly, not res.user.role.
  return res.data;
}

// NOTE: only student self-registration exists on the backend
// (POST /auth/register/student). Admins and Professors are created by an
// Admin/SuperAdmin instead (see adminService.createProfessor /
// superadminService.addAdmin) — there is no general /auth/register.
export async function register(fullName, email, password, studentId, section, yearLevel) {
  const res = await apiClient.post('/auth/register/student', {
    name: fullName, // backend field is "name", not "fullName"
    email,
    password,
    studentId,
    section,
    yearLevel,
  }, { skipAuth: true });
  return res.data;
}

// ✅ Confirmed working. Sends a one-time OTP code to the student/user's email.
export async function sendPasswordResetCode(email) {
  const res = await apiClient.post('/auth/forgot-password', { email }, { skipAuth: true });
  return res.data;
}

// ✅ Confirmed working — same OTP flow as sendPasswordResetCode, for the
// "resend code" action.
export async function resendPasswordResetCode(email) {
  const res = await apiClient.post('/auth/forgot-password/resend', { email });
  return res.data;
}

// ✅ Confirmed working. NOTE: the backend's field is "otp", not "code" —
// make sure ForgotPassword.jsx sends { email, otp, newPassword }.
export async function resetPassword(email, otp, newPassword) {
  const res = await apiClient.post('/auth/reset-password', { email, otp, newPassword }, { skipAuth: true });
  return res.data;
}

// ✅ Confirmed working. Requires the user to already be logged in (token
// sent automatically via apiClient's interceptor).
export async function changePassword(currentPassword, newPassword) {
  const res = await apiClient.post('/auth/change-password', { currentPassword, newPassword });
  return res.data;
}