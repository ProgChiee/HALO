// API routes verified against the controllers in HALO.zip.
import apiClient from '../apiClient';

export async function openSession(moduleId) {
  const res = await apiClient.post(`/student/mentor/open/${moduleId}`);
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