// API routes verified against the controllers in HALO.zip.
import apiClient from '../apiClient';

export async function openSession(moduleId, config = {}) {
  if (!/^\d+$/.test(String(moduleId)) || Number(moduleId) <= 0) {
    throw new Error('The lesson response did not include a valid module ID.');
  }
  // POST creates/resumes a session. GET is not part of this controller contract.
  const res = await apiClient.post(`/student/mentor/open/${moduleId}`, null, config);
  return res.data;
}

export async function sendMessage(sessionId, message) {
  const res = await apiClient.post(`/student/mentor/message/${sessionId}`, { message });
  return res.data;
}

export async function getConversation(sessionId) {
  const res = await apiClient.get(`/student/mentor/session/${sessionId}`);
  return res.data;
}