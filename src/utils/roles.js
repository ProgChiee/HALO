// Centralized role constants — import this everywhere instead of
// typing raw strings like 'admin', so typos get caught by your editor.

export const ROLES = {
  SUPERADMIN: 'superadmin',
  ADMIN: 'admin',
  PROFESSOR: 'professor',
  STUDENT: 'student',
};

export const ROLE_LABELS = {
  [ROLES.SUPERADMIN]: 'Super Admin',
  [ROLES.ADMIN]: 'Admin',
  [ROLES.PROFESSOR]: 'Professor',
  [ROLES.STUDENT]: 'AHRT Student',
};

// Default landing route after login, per role
export const ROLE_HOME = {
  [ROLES.SUPERADMIN]: '/superadmin',
  [ROLES.ADMIN]: '/admin',
  [ROLES.PROFESSOR]: '/professor',
  [ROLES.STUDENT]: '/student',
};
