// Mock data for the Admin "AI Agent Usage Monitor" page.
//
// TODO: once the real AI Mentor backend is ready, this should come from
// an aggregation endpoint (e.g. GET /api/admin/ai-usage) that counts
// actual chat interactions per subject/student, grouped server-side.
// The subject titles/ids here intentionally match src/data/shared/
// subjectsCatalog.js so the numbers look tied to real subjects.

export const mockAiUsageSummary = {
  totalQueriesToday: 342,
  totalQueriesThisWeek: 1876,
  activeStudentsToday: 94,
  avgQueriesPerStudent: 4.2,
};

export const mockAiUsageBySubject = [
  { id: 's205', subject: 'Front Office Operations', year: '2nd Year', queries: 312, activeStudents: 41 },
  { id: 's108', subject: 'Food and Beverage Services', year: '1st Year', queries: 287, activeStudents: 38 },
  { id: 's101', subject: 'Introduction to Quick Food Service', year: '1st Year', queries: 254, activeStudents: 44 },
  { id: 's206', subject: 'Housekeeping Operations', year: '2nd Year', queries: 198, activeStudents: 29 },
  { id: 's203', subject: 'International Cuisine', year: '2nd Year', queries: 176, activeStudents: 25 },
  { id: 's102', subject: 'Kitchen Essentials and Basic Food Preparation', year: '1st Year', queries: 163, activeStudents: 33 },
  { id: 's207', subject: 'Bread and Pastry Production', year: '2nd Year', queries: 141, activeStudents: 22 },
  { id: 's109', subject: 'Fundamentals of Lodging Operations', year: '1st Year', queries: 98, activeStudents: 19 },
  { id: 's202', subject: 'Bar and Beverage Management', year: '2nd Year', queries: 84, activeStudents: 15 },
  { id: 's106', subject: 'Philippine Regional Cuisine', year: '1st Year', queries: 67, activeStudents: 14 },
];

export const mockTopAskedTopics = [
  { id: 1, topic: 'How to properly greet a hotel guest at check-in', subject: 'Front Office Operations', count: 47 },
  { id: 2, topic: 'Difference between a la carte and table d\u2019h\u00f4te menu', subject: 'Food and Beverage Services', count: 39 },
  { id: 3, topic: 'Steps in making a mother sauce', subject: 'Introduction to Quick Food Service', count: 34 },
  { id: 4, topic: 'Proper bed-making technique for turndown service', subject: 'Housekeeping Operations', count: 28 },
  { id: 5, topic: 'How to handle an overbooking situation', subject: 'Front Office Operations', count: 22 },
];