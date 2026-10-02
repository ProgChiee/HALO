import { useCallback, useEffect, useMemo, useState } from 'react';
import { AuthContext } from './useAuth';
import { clearSession, readSession, writeSession } from '../../utils/session';

export function AuthProvider({ children }) {
  const [session, setSession] = useState(() => readSession());

  useEffect(() => {
    const syncSession = () => setSession(readSession());
    window.addEventListener('storage', syncSession);
    window.addEventListener('halo:session-expired', syncSession);
    return () => {
      window.removeEventListener('storage', syncSession);
      window.removeEventListener('halo:session-expired', syncSession);
    };
  }, []);

  const login = useCallback((user, token, rememberMe = true) => {
    setSession(writeSession(user, token, rememberMe));
  }, []);

  const logout = useCallback(() => {
    clearSession();
    setSession({ user: null, token: null });
  }, []);

  const value = useMemo(() => ({
    ...session,
    role: session.user?.role ?? null,
    isAuthenticated: Boolean(session.user && session.token),
    isLoading: false,
    login,
    logout,
  }), [session, login, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
