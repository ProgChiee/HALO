// Mock data for the Admin "Quiz Performance Overview" tab.
//
// TODO: once the backend is ready, this should come from an aggregation
// endpoint (e.g. GET /api/admin/quiz-performance) that computes averages
// straight from stored quiz attempts (see quizData.js / submitQuizResult
// on the student side for the shape of a single attempt).

export const mockQuizSummary = {
  totalAttemptsToday: 128,
  avgScoreOverall: 76, // percent
  passRateOverall: 82, // percent, using 60% as the passing mark
  totalQuizzesTaken: 3140,
};

export const mockQuizPerformanceBySubject = [
  { id: 's101', subject: 'Introduction to Quick Food Service', year: '1st Year', avgScore: 84, attempts: 412 },
  { id: 's108', subject: 'Food and Beverage Services', year: '1st Year', avgScore: 81, attempts: 389 },
  { id: 's205', subject: 'Front Office Operations', year: '2nd Year', avgScore: 79, attempts: 356 },
  { id: 's102', subject: 'Kitchen Essentials and Basic Food Preparation', year: '1st Year', avgScore: 74, attempts: 301 },
  { id: 's203', subject: 'International Cuisine', year: '2nd Year', avgScore: 71, attempts: 244 },
  { id: 's206', subject: 'Housekeeping Operations', year: '2nd Year', avgScore: 68, attempts: 228 },
  { id: 's207', subject: 'Bread and Pastry Production', year: '2nd Year', avgScore: 64, attempts: 189 },
  { id: 's109', subject: 'Fundamentals of Lodging Operations', year: '1st Year', avgScore: 58, attempts: 142 },
];

// Lowest-scoring quizzes across all subjects — these are the ones that
// likely need the content/questions reviewed by the professor.
export const mockLowestScoringQuizzes = [
  { id: 1, quizTitle: 'Week 5: Front Desk Emergency Procedures', subject: 'Front Office Operations', avgScore: 52, attempts: 38 },
  { id: 2, quizTitle: 'Week 3: Bread Fermentation Basics', subject: 'Bread and Pastry Production', avgScore: 55, attempts: 41 },
  { id: 3, quizTitle: 'Week 6: Reservation Systems', subject: 'Fundamentals of Lodging Operations', avgScore: 58, attempts: 29 },
  { id: 4, quizTitle: 'Week 2: Regional Sauce Pairings', subject: 'International Cuisine', avgScore: 61, attempts: 47 },
  { id: 5, quizTitle: 'Week 4: Laundry & Linen Management', subject: 'Housekeeping Operations', avgScore: 63, attempts: 33 },
];