import { useLayoutEffect } from 'react';
import { useLocation } from 'react-router-dom';
import { useAuth } from '../context/login/useAuth';

// Login currently exposes email, not a database ID. Never use an acting target identity.
function themeAccountKey(user) {
  return user?.email && user?.role
    ? `${user.role}:${encodeURIComponent(user.email.trim().toLowerCase())}`
    : null;
}

export default function AccountTheme() {
  const { user, isAuthenticated } = useAuth();
  const { pathname } = useLocation();
  // Only authenticated application shells may use a saved preference. All public/recovery pages stay light.
  const inApplication = /^\/(superadmin|admin|professor|student)(\/|$)/.test(pathname);
  const account = isAuthenticated && inApplication ? themeAccountKey(user) : null;
  useLayoutEffect(() => {
    window.haloTheme?.bindAccount(account);
  }, [account]);
  return null;
}

