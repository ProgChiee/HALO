// API routes verified against the controllers in HALO.zip.
import defaultClient from '../apiClient';

export function createMentorService(apiClient = defaultClient) {

async function openSession(moduleId, config = {}) {
  if (!/^\d+$/.test(String(moduleId)) || Number(moduleId) <= 0) {
    throw new Error('The lesson response did not include a valid module ID.');
  }
  // POST creates/resumes a session. GET is not part of this controller contract.
  const res = await apiClient.post(`/student/mentor/open/${moduleId}`, null, { timeout: 90000, ...config });
  return res.data;
}

async function sendMessage(sessionId, message, config = {}) {
  const { requestId, ...requestConfig } = config;
  const res = await apiClient.post(`/student/mentor/message/${sessionId}`, { message, requestId }, { timeout: 90000, ...requestConfig });
  return res.data;
}

async function getExchange(sessionId, requestId, config = {}) {
  const res = await apiClient.get(`/student/mentor/message/${sessionId}/request/${requestId}`, config);
  return res.data;
}

async function getConversation(sessionId, beforeId = null, config = {}) {
  const res = await apiClient.get(`/student/mentor/session/${sessionId}`, { ...config, params: { beforeId } });
  return res.data;
}

return { openSession, sendMessage, getExchange, getConversation };
}
export const { openSession, sendMessage, getExchange, getConversation } = createMentorService();
