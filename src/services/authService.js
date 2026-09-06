// Centralized authentication service.
//
// TODO: swap the mock logic inside each function below for a real API call
// (e.g. axios.post('/api/auth/login', {...})) once your backend is ready.
// Every function already returns a Promise, so Login.jsx / Register.jsx /
// ForgotPassword.jsx won't need to change — only this file will.

function delay(ms = 700) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export async function login(email, password) {
  await delay();
  // TODO: const res = await axios.post('/api/auth/login', { email, password });
  // return res.data.user;
  //
  // Mock-only: derive the role from a keyword in the email so all 4 roles
  // can actually be tested through the real Login form (the app has strict
  // per-role route protection — see AppRoutes.jsx — so logging everyone in
  // as the same role would lock you out of 3 of the 4 portals). Once the
  // real backend is connected, the role will come from res.data.user
  // instead, and this keyword logic can be deleted.
  const lower = email.toLowerCase();
  let role = 'admin';
  if (lower.includes('superadmin')) role = 'superadmin';
  else if (lower.includes('professor') || lower.includes('prof')) role = 'professor';
  else if (lower.includes('student')) role = 'student';

  return { id: 1, name: 'Juan Dela Cruz', email, role };
}

export async function register(fullName, email, password) {
  await delay();
  // TODO: await axios.post('/api/auth/register', { fullName, email, password });
  return { success: true };
}

export async function sendPasswordResetCode(email) {
  await delay();
  // TODO: await axios.post('/api/auth/forgot-password', { email });
  return { success: true };
}

export async function resendPasswordResetCode(email) {
  await delay(500);
  // TODO: await axios.post('/api/auth/forgot-password/resend', { email });
  return { success: true };
}

export async function resetPassword(email, code, newPassword) {
  await delay();
  // TODO: await axios.post('/api/auth/reset-password', { email, code, newPassword });
  return { success: true };
}

// Used by the "Change password" action on every role's Profile page
// (Student, Professor, Admin, Superadmin) — one shared function since the
// logic is identical regardless of role.
export async function changePassword(currentPassword, newPassword) {
  await delay();
  // TODO: await apiClient.post('/auth/change-password', { currentPassword, newPassword });
  // The real backend MUST verify currentPassword matches before accepting
  // the change — this mock has no way to check that, so it always
  // "succeeds" here for demo purposes.
  return { success: true };
}