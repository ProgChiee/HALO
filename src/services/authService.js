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
  return { id: 1, name: 'Juan Dela Cruz', email, role: 'student' };
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