// Centralized axios instance for all backend calls.
//
// Every service file (authService.js, adminService.js, etc.) should import
// this instead of calling axios directly — that way the base URL, auth
// token, and error handling are all in one place.

import axios from 'axios';

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Attach the auth token (if any) to every outgoing request.
// NOTE: this reads from 'halo_token'. AuthContext.jsx currently stores the
// full user object under 'halo_user' instead of a separate token — that
// still needs to be updated once the backend issues real tokens (see the
// auth/token storage step).
apiClient.interceptors.request.use((config) => {
  const token =
    localStorage.getItem('halo_token') || sessionStorage.getItem('halo_token');

  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  return config;
});

// Handle expired/invalid sessions globally so individual services don't
// each need their own 401 handling.
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('halo_token');
      sessionStorage.removeItem('halo_token');
      localStorage.removeItem('halo_user');
      sessionStorage.removeItem('halo_user');

      if (window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
    }

    return Promise.reject(error);
  }
);

export default apiClient;