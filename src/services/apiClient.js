import axios from 'axios';
import { clearSession, readSession } from '../utils/session';
import { logStudentError } from '../utils/studentDiagnostics';

// VITE_API_URL must include /api; same-origin deployments use /api by default.
const apiClient = axios.create({ baseURL: import.meta.env.VITE_API_URL || '/api', timeout: 15000 });

apiClient.interceptors.request.use((config) => {
  // Preserve existing long-running upload/generation requests; no automatic retries.
  if (config.timeout === 15000 && (config.data instanceof FormData || /\/(generate)(?:\/|$)/.test(config.url ?? ''))) config.timeout = 180000;
  const { token } = readSession();
  if (token && !config.skipAuth) config.headers.Authorization = 'Bearer ' + token;
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (import.meta.env.DEV && error.code !== 'ERR_CANCELED') {
      // Never log tokens, request bodies, student questions, or file contents.
      if (/^(?:\/api)?\/student(?:\/|$)/.test(error.config?.url ?? '')) {
        logStudentError('request-failed', error);
      } else console.warn('[HALO API]', {
        method: error.config?.method?.toUpperCase(),
        endpoint: (error.config?.url ?? '').split('?')[0].replace(/[^/a-zA-Z0-9_-]/g, '').slice(0, 160),
        status: error.response?.status ?? 'NO_RESPONSE',
        code: /^[A-Z_]{1,64}$/.test(error.response?.data?.code ?? error.code ?? '') ? (error.response?.data?.code ?? error.code) : 'REQUEST_FAILED',
      });
    }
    const sentToken = error.config?.headers?.Authorization;
    const { token } = readSession();
    // An old request or an invalid password on a public form must not
    // invalidate a newer authenticated session.
    if (error.response?.status === 401 && error.response?.data?.code === 'INVALID_OR_EXPIRED_TOKEN'
      && token && sentToken === 'Bearer ' + token) {
      clearSession();
      window.dispatchEvent(new Event('halo:session-expired'));
    }
    return Promise.reject(error);
  },
);

export default apiClient;
