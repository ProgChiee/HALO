import axios from 'axios';
import apiClient from '../apiClient';
import { createProfessorService } from '../professor/professorService';

export const getProfessorTargets = async (page, search, config = {}) =>
  (await apiClient.get('/admin/acting/targets', { ...config, params: { role: 'PROFESSOR', page, size: 20, search } })).data;
export const createProfessorSession = async (targetUserId, config = {}) =>
  (await apiClient.post('/admin/acting/sessions', { targetUserId, targetRole: 'PROFESSOR' }, config)).data;
export const validateProfessorSession = async (id, config = {}) =>
  (await apiClient.get(`/admin/acting/sessions/${encodeURIComponent(id)}`, config)).data;
export const revokeProfessorSession = async (id) => apiClient.delete(`/admin/acting/sessions/${encodeURIComponent(id)}`);

// This adapter is instance-scoped: never rewrite the shared Axios client's defaults.
export function createActingProfessorScope(sessionId, onInvalid) {
  let active = true;
  const pending = new Set();
  async function request(method, path, data, config = {}) {
    if (!active) throw new axios.CanceledError();
    if (!path.startsWith('/professor/')) throw new Error('Unsupported Professor operation');
    const controller = new AbortController();
    pending.add(controller);
    const abort = () => controller.abort();
    config.signal?.addEventListener('abort', abort, { once: true });
    if (config.signal?.aborted) controller.abort();
    try {
      const response = await apiClient.request({ ...config, method, data,
        url: '/admin/acting/professor/' + path.slice('/professor/'.length),
        signal: controller.signal, headers: { ...config.headers, 'X-Acting-Session': sessionId } });
      if (!active || controller.signal.aborted) throw new axios.CanceledError();
      return response;
    } catch (error) {
      if (!active || controller.signal.aborted) throw new axios.CanceledError();
      if (['ACTING_SESSION_INVALID', 'INVALID_TARGET_ROLE', 'ACTING_TARGET_INACTIVE'].includes(error.response?.data?.code)
          || error.response?.status === 403) onInvalid();
      throw error;
    } finally {
      pending.delete(controller);
      config.signal?.removeEventListener('abort', abort);
    }
  }
  const client = {
    get: (path, config) => request('get', path, undefined, config),
    delete: (path, config) => request('delete', path, undefined, config),
    post: (path, data, config) => request('post', path, data, config),
    put: (path, data, config) => request('put', path, data, config),
  };
  return { api: createProfessorService(client), resume() { active = true; },
    dispose() { active = false; pending.forEach(c => c.abort()); pending.clear(); } };
}
