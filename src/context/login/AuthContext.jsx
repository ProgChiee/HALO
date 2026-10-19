import apiClient from '../../services/apiClient';
import ProfilePhotoProvider from './ProfilePhotoProvider';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AuthContext } from './useAuth';
import { clearSession, readSession, writeSession } from '../../utils/session';

export function AuthProvider({ children }) {
  const logoutRequest = useRef(null);
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

  const updateFullName = useCallback(async fullName => {
    const captured = readSession();
    const { data } = await apiClient.patch('/profile', { fullName });
    const current = readSession();
    if (!captured.token || current.token !== captured.token || current.user?.email !== captured.user?.email) {
      throw Object.assign(new Error('Account changed. Please reopen your profile.'), { code: 'PROFILE_ACCOUNT_CHANGED' });
    }
    setSession(writeSession({ ...current.user, name: data.name }, current.token, localStorage.getItem('halo_token') === current.token));
    return data.name;
  }, []);

  const logout = useCallback(() => {
    if (logoutRequest.current) return logoutRequest.current;
    const { token } = readSession();
    const request = (async () => {
      if (token) {
        try {
          await apiClient.post('/auth/logout', undefined, { skipAuth: true, headers: { Authorization: 'Bearer ' + token } });
        } catch (error) {
          // A retry after a lost success response can report an already-revoked token.
          if (error.response?.status !== 401 || error.response?.data?.code !== 'INVALID_OR_EXPIRED_TOKEN') throw error;
        }
      }
      const current = readSession();
      if (current.token && current.token !== token) throw new Error('Account changed during logout');
      window.haloTheme?.bindAccount(null);
      clearSession();
      setSession({ user: null, token: null });
    })();
    logoutRequest.current = request.finally(() => { logoutRequest.current = null; });
    return logoutRequest.current;
  }, []);

  const value = useMemo(() => ({
    ...session,
    role: session.user?.role ?? null,
    isAuthenticated: Boolean(session.user && session.token),
    isLoading: false,
    login,
    logout,
    updateFullName,
  }), [session, login, logout, updateFullName]);

  return <AuthContext.Provider value={value}><ProfilePhotoProvider>{children}</ProfilePhotoProvider></AuthContext.Provider>;
}

