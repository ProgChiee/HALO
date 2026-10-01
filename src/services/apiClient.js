import axios from 'axios';
import { clearSession, readSession } from '../utils/session';

// VITE_API_URL must include /api; same-origin deployments use /api by default.
const apiClient = axios.create({ baseURL: import.meta.env.VITE_API_URL || '/api' });

apiClient.interceptors.request.use((config) => {
  const { token } = readSession();
  if (token && !config.skipAuth) config.headers.Authorization = 'Bearer ' + token;
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const sentToken = error.config?.headers?.Authorization;
    const { token } = readSession();
    // An old request or an invalid password on a public form must not
    // invalidate a newer authenticated session.
    if (error.response?.status === 401 && token && sentToken === 'Bearer ' + token) {
      clearSession();
      window.dispatchEvent(new Event('halo:session-expired'));
    }
    return Promise.reject(error);
  },
);

export default apiClient;
