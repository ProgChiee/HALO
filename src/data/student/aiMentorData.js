// Mock data for the AI Mentor chat (Lesson) page.
//
// TODO: once the real AI Mentor backend is ready, replace:
//   1. getSeedMessages() — with a GET call that fetches chat history
//      for this student + lesson (e.g. GET /api/lessons/:id/messages)
//   2. getMockReply() — with a real API call to your AI model
//      (e.g. POST /api/ai-mentor/chat with the student's message),
//      streaming or awaiting the real response instead of the
//      canned replies + setTimeout delay used here.

export function getSeedMessages(topicTitle, weekLabel) {
  return [
    {
      id: 1,
      sender: 'ai',
      text: `Welcome to ${weekLabel} of "${topicTitle}"! Today we'll walk through the key concepts together. Let me know if anything's unclear — I'm here to help.`,
    },
    {
      id: 2,
      sender: 'user',
      text: 'Can you give me a quick overview before we start?',
    },
    {
      id: 3,
      sender: 'ai',
      text: "Sure! We'll cover the core ideas step by step, with short checks along the way so you can confirm your understanding before moving on. Ready when you are.",
    },
  ];
}

const MOCK_REPLIES = [
  "That's a great question! Let's break it down step by step.",
  "Good thinking. Here's another way to look at it...",
  "You're on the right track. Want me to give you an example?",
  "Let's review that concept together before moving to the next topic.",
  "Nice! That connects well to what we covered earlier in this lesson.",
];

export function getMockReply() {
  const random = MOCK_REPLIES[Math.floor(Math.random() * MOCK_REPLIES.length)];
  return random;
}

export const LESSON_TABS = ['Topics', 'Reading', 'Video', 'Summary'];