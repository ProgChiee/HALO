import { createContext, useContext, useState, useEffect } from 'react';

const AuthContext = createContext(null);

/**
 * Wrap the whole app with <AuthProvider> in App.jsx.
 * Provides: user, role, login(), logout(), isLoading
 *
 * Roles used across HALO: 'superadmin' | 'admin' | 'professor' | 'student'
 */
export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [isLoading, setIsLoading] = useState(true);

  // On first load, check localStorage first (remembered), then sessionStorage
  useEffect(() => {
    const remembered = localStorage.getItem('halo_user');
    const sessionOnly = sessionStorage.getItem('halo_user');
    const savedUser = remembered || sessionOnly;

    if (savedUser) {
      setUser(JSON.parse(savedUser));
    }
    setIsLoading(false);
  }, []);

  function login(userData, rememberMe = true) {
    // userData shape: { id, name, email, role }
    setUser(userData);

    if (rememberMe) {
      localStorage.setItem('halo_user', JSON.stringify(userData));
      sessionStorage.removeItem('halo_user');
    } else {
      // Only persists for this browser tab/session
      sessionStorage.setItem('halo_user', JSON.stringify(userData));
      localStorage.removeItem('halo_user');
    }
  }

  function logout() {
    setUser(null);
    localStorage.removeItem('halo_user');
    sessionStorage.removeItem('halo_user');
  }

  const value = {
    user,
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