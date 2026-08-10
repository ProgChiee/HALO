// Centralized service for the Quiz feature.
//
// TODO: swap the mock logic below for real API calls once your backend is
// ready. getQuiz() should fetch the actual quiz questions for a given
// lesson; submitQuiz() should record the student's score.

import { getQuizForLesson, submitQuizResult } from '../../data/student/quizData';

function delay(ms = 300) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export async function getQuiz(topicId, weekId) {
  await delay();
  // TODO: const res = await axios.get(`/api/lessons/${topicId}/${weekId}/quiz`); return res.data;
  return getQuizForLesson(topicId, weekId);
}

export async function submitQuiz(topicId, weekId, score, total) {
  await delay();
  // TODO: await axios.post('/api/quiz-attempts', { topicId, weekId, score, total });
  return submitQuizResult(topicId, weekId, score, total);
}