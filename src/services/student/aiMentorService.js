// Centralized service for the AI Mentor chat feature.
//
// Wired to the real backend (com.ptc.halo.controller.StudentMentorController).
// This is a SESSION-based flow, not a simple "get history / send message"
// pair — see the shapes below.

import apiClient from '../apiClient';

// Opens (or resumes) the mentor conversation for a lesson module.
// Returns { sessionId, moduleId, messages: [{ id, sender, message, createdAt }] }
// Call this on page load — it's the one that gives you the full history.
export async function openSession(moduleId) {
  const res = await apiClient.post(`/student/mentor/open/${moduleId}`);
  return res.data;
}

// Starts a NEW session for a module (separate from openSession — confirm
// with your backend team whether this creates an additional session even
// if one already exists, or whether the UI should ever call this at all
// instead of always using openSession).
// Returns { sessionId, moduleId, haloMessage }
export async function startSession(moduleId) {
  const res = await apiClient.post(`/student/mentor/start/${moduleId}`);
  return res.data;
}

// Sends a message in an existing session. Returns only the reply, not the
// full history: { sessionId, moduleId, haloMessage }
export async function sendMessage(sessionId, message) {
  const res = await apiClient.post(`/student/mentor/message/${sessionId}`, { message });
  return res.data;
}

// Refetches the full conversation for a session (e.g. if you need to
// reload after an error, without re-opening).
// Returns { sessionId, moduleId, messages: [...] }
export async function getConversation(sessionId) {
  const res = await apiClient.get(`/student/mentor/session/${sessionId}`);
  return res.data;
}