const VALID_ROLES = ['superadmin', 'admin', 'professor', 'student'];

export function readSession(storages = [localStorage, sessionStorage]) {
  for (const storage of storages) {
    try {
      const user = JSON.parse(storage.getItem('halo_user'));
      const token = storage.getItem('halo_token');
      if (VALID_ROLES.includes(user?.role) && typeof user?.name === 'string'
        && typeof user?.email === 'string' && token?.trim()) {
        return { user, token };
      }
    } catch {
      // Invalid or unavailable storage is treated as signed out.
    }
  }
  return { user: null, token: null };
}

export function clearSession() {
  for (const storage of [localStorage, sessionStorage]) {
    storage.removeItem('halo_user');
    storage.removeItem('halo_token');
  }
}
