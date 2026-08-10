// Centralized service for the AI Mentor chat feature.
//
// TODO: swap the mock logic below for real calls to your AI Mentor backend
// once it's ready. getChatHistory() should fetch past messages for this
// student + lesson; sendMessageToMentor() should send the student's
// message and return the AI's real reply.

import { getSeedMessages, getMockReply } from '../../data/student/aiMentorData';

function delay(ms = 300) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export async function getChatHistory(topicTitle, weekLabel) {
  await delay();
  // TODO: const res = await axios.get(`/api/lessons/${lessonId}/messages`); return res.data;
  return getSeedMessages(topicTitle, weekLabel);
}

export async function sendMessageToMentor(message, lessonId) {
  await delay(1000);
  // TODO: const res = await axios.post('/api/ai-mentor/chat', { message, lessonId });
  // return res.data.reply;
  return getMockReply();
}