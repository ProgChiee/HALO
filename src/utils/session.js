import { parseLoginResponse, ROLE_FROM_API } from './backendContract.js';

function normalizeUser(user, token) {
  const backendRole = user?.backendRole ?? (Object.hasOwn(ROLE_FROM_API, user?.role)
    ? user.role : Object.keys(ROLE_FROM_API).find((key) => ROLE_FROM_API[key] === user?.role));
  const session = parseLoginResponse({ ...user, role: backendRole, token });
  if (user.role !== backendRole && user.role !== session.user.role) throw new Error('Session role mismatch');
  return session;
}

export function readSession(storages = [localStorage, sessionStorage]) {
  for (const storage of storages) {
    try {
      const user = JSON.parse(storage.getItem('halo_user'));
      const token = storage.getItem('halo_token');
      return normalizeUser(user, token);
    } catch {
      // Invalid or incomplete storage cannot authenticate a user.
    }
  }
  return { user: null, token: null };
}

export function clearSession(storages = [localStorage, sessionStorage]) {
  for (const storage of storages) {
    storage.removeItem('halo_user');
    storage.removeItem('halo_token');
  }
}

export function writeSession(user, token, rememberMe = true, storages = [localStorage, sessionStorage]) {
  const session = normalizeUser(user, token);
  clearSession(storages);
  const storage = storages[rememberMe ? 0 : 1];
  storage.setItem('halo_user', JSON.stringify({ ...session.user, role: session.user.backendRole }));
  storage.setItem('halo_token', session.token);
  return session;
}
