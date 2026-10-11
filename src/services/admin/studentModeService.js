import axios from 'axios';
import apiClient from '../apiClient';
import { createStudentService } from '../student/studentService';
import { createQuizService } from '../student/quizService';
import { createMentorService } from '../student/aiMentorService';

export const getStudentTargets = async (page, search, config = {}) =>
  (await apiClient.get('/admin/acting/targets', { ...config, params: { role: 'STUDENT', page, size: 20, search } })).data;
export const createStudentSession = async (targetUserId, config = {}) =>
  (await apiClient.post('/admin/acting/sessions', { targetUserId, targetRole: 'STUDENT' }, config)).data;
export const validateStudentSession = async (id, config = {}) =>
  (await apiClient.get(`/admin/acting/sessions/${encodeURIComponent(id)}`, config)).data;
export const revokeStudentSession = async (id) => apiClient.delete(`/admin/acting/sessions/${encodeURIComponent(id)}`);

export const createStudentPreview = async (targetUserId, config = {}) =>
  (await apiClient.post('/admin/preview/student/sessions', { targetUserId }, config)).data;
export const validateStudentPreview = async (id, config = {}) =>
  (await apiClient.get(`/admin/preview/student/sessions/${encodeURIComponent(id)}`, config)).data;
export const revokeStudentPreview = async id => apiClient.delete(`/admin/preview/student/sessions/${encodeURIComponent(id)}`);

// This adapter is instance-scoped: never rewrite the shared Axios client's defaults.
export function createActingStudentScope(sessionId, onInvalid, preview = false) {
  let active = true;
  const pending = new Set();
  async function request(method, path, data, config = {}) {
    if (!active) throw new axios.CanceledError();
    if (!path.startsWith('/student/')) throw new Error('Unsupported Student operation');
    const controller = new AbortController();
    pending.add(controller);
    const abort = () => controller.abort();
    config.signal?.addEventListener('abort', abort, { once: true });
    if (config.signal?.aborted) controller.abort();
    try {
      const response = await apiClient.request({ ...config, method, data,
        url: (preview ? '/admin/preview/student/' : '/admin/acting/student/') + path.slice('/student/'.length),
        signal: controller.signal, headers: { ...config.headers, 'X-Acting-Session': sessionId } });
      if (!active || controller.signal.aborted) throw new axios.CanceledError();
      return response;
    } catch (error) {
      if (!active || controller.signal.aborted) throw new axios.CanceledError();
      if (['ACTING_SESSION_INVALID', 'INVALID_TARGET_ROLE', 'ACTING_TARGET_INACTIVE'].includes(error.response?.data?.code)) onInvalid();
      else if ([403, 404].includes(error.response?.status)) {
        // A lesson eligibility denial is not necessarily a lost acting session.
        try { await (preview ? validateStudentPreview : validateStudentSession)(sessionId, { signal: controller.signal }); }
        catch (validation) {
          if (active && !controller.signal.aborted && [400, 403, 404, 409].includes(validation.response?.status)) onInvalid();
        }
      }
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
  return { api: { ...createStudentService(client), ...createQuizService(client), ...createMentorService(client) }, resume() { active = true; },
    dispose() { active = false; pending.forEach(c => c.abort()); pending.clear(); } };
}
