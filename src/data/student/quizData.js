// Mock data for the Quiz feature.
//
// TODO: once the backend is ready, replace getQuizForLesson() with a real
// API call (e.g. GET /api/lessons/:topicId/:weekId/quiz) and
// submitQuizResult() with a POST call that records the student's score
// (e.g. POST /api/quiz-attempts). The Quiz.jsx component won't need to
// change shape-wise as long as the response keeps this structure.

const DEFAULT_QUIZ = {
  title: 'Lesson Quiz',
  questions: [
    {
      id: 1,
      question: 'What is the first step in proper food handling?',
      options: [
        'Washing your hands',
        'Wearing gloves',
        'Cooking the food',
        'Plating the dish',
      ],
      correctIndex: 0,
    },
    {
      id: 2,
      question: 'Which temperature range is considered the "danger zone" for food safety?',
      options: [
        '0°C – 4°C',
        '5°C – 60°C',
        '60°C – 100°C',
        '100°C and above',
      ],
      correctIndex: 1,
    },
    {
      id: 3,
      question: 'What does "mise en place" mean in a kitchen setting?',
      options: [
        'Cleaning as you go',
        'Everything in its place, prepared in advance',
        'Plating the final dish',
        'Taking customer orders',
      ],
      correctIndex: 1,
    },
    {
      id: 4,
      question: 'Which of these best describes good customer service in hospitality?',
      options: [
        'Speaking as little as possible to guests',
        'Anticipating guest needs and responding promptly',
        'Only helping guests who ask directly',
        'Prioritizing speed over guest comfort',
      ],
      correctIndex: 1,
    },
    {
      id: 5,
      question: 'Why is cross-contamination prevention important in food service?',
      options: [
        'It makes food look more appealing',
        'It reduces food costs',
        'It prevents the spread of harmful bacteria',
        'It speeds up cooking time',
      ],
      correctIndex: 2,
    },
  ],
};

// Add topic/week-specific quizzes here as needed, keyed as "topicId:weekId".
// Falls back to DEFAULT_QUIZ if no specific quiz is found — useful since
// the generic "Start quiz" buttons on Dashboard/Badges/Profile don't
// always have a specific lesson in mind.
const QUIZ_BANK = {
  // 't2:w2': { title: 'Kitchen Essentials — Week 2 Quiz', questions: [...] },
};

export function getQuizForLesson(topicId, weekId) {
  const key = `${topicId}:${weekId}`;
  return QUIZ_BANK[key] || DEFAULT_QUIZ;
}

export function submitQuizResult(topicId, weekId, score, total) {
  // TODO: POST the result to your backend here.
  console.log(`Quiz submitted for ${topicId}/${weekId}: ${score}/${total}`);
}