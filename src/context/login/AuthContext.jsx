import { createContext, useContext, useState, useEffect } from 'react';

const AuthContext = createContext(null);

/**
 * Wrap the whole app with <AuthProvider> in App.jsx.
 * Provides: user, role, token, login(), logout(), isLoading
 *
 * Roles used across HALO: 'superadmin' | 'admin' | 'professor' | 'student'
 */
export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(null);
  const [isLoading, setIsLoading] = useState(true);

  // On first load, check localStorage first (remembered), then sessionStorage
  useEffect(() => {
    const rememberedUser = localStorage.getItem('halo_user');
    const sessionUser = sessionStorage.getItem('halo_user');
    const savedUser = rememberedUser || sessionUser;

    const rememberedToken = localStorage.getItem('halo_token');
    const sessionToken = sessionStorage.getItem('halo_token');
    const savedToken = rememberedToken || sessionToken;

    if (savedUser) {
      try {
        setUser(JSON.parse(savedUser));
        if (savedToken) setToken(savedToken);
      } catch (err) {
        // Corrupted/malformed data (e.g. manually edited in DevTools, or
        // written by an old app version) — clear it and treat as logged
        // out, instead of letting a JSON parse error crash the whole app
        // (AuthProvider wraps every page).
        console.error('Failed to parse saved user, clearing storage:', err);
        localStorage.removeItem('halo_user');
        sessionStorage.removeItem('halo_user');
        localStorage.removeItem('halo_token');
        sessionStorage.removeItem('halo_token');
      }
    }
    setIsLoading(false);
  }, []);

  // userData shape: { id, name, email, role }
  // authToken: the token returned by the backend on login (undefined while
  // authService.js is still mocked — apiClient.js simply won't attach an
  // Authorization header until a real token exists).
  function login(userData, authToken, rememberMe = true) {
    setUser(userData);
    setToken(authToken ?? null);

    const storage = rememberMe ? localStorage : sessionStorage;
    const otherStorage = rememberMe ? sessionStorage : localStorage;

    storage.setItem('halo_user', JSON.stringify(userData));
    otherStorage.removeItem('halo_user');

    if (authToken) {
      storage.setItem('halo_token', authToken);
      otherStorage.removeItem('halo_token');
    } else {
      storage.removeItem('halo_token');
      otherStorage.removeItem('halo_token');
    }
  }

  function logout() {
    setUser(null);
    setToken(null);
    localStorage.removeItem('halo_user');
    sessionStorage.removeItem('halo_user');
    localStorage.removeItem('halo_token');
    sessionStorage.removeItem('halo_token');
  }

  const value = {
    user,
    token,
    role: user?.role ?? null,
    isAuthenticated: !!user,
    isLoading,
    login,
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// Custom hook so components just call useAuth() instead of useContext(AuthContext)
export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider');
  return ctx;
}