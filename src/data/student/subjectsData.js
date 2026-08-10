// Mock data for the Subjects (Learning Hub) page.
//
// This mirrors the shape that Admin's Subject/Topic/Week management
// screens will eventually produce. Once Admin CRUD + backend are ready,
// replace mockSubjectGroups below with a real API call
// (e.g. GET /api/subjects?studentId=...) — the Subjects.jsx component
// won't need to change as long as the response keeps this shape.

export const YEAR_FILTERS = ['All Years', '1st Year', '2nd Year'];

export const mockSubjectsOverview = {
  topicsDone: 2,
  topicsTotal: 17,
};

export const mockSubjectGroups = [
  {
    id: 'y1',
    year: '1st Year',
    subjectName: 'Learning Hub',
    topics: [
      {
        id: 't1',
        title: 'Introduction to food service',
        lessonsCount: 8,
        status: 'completed', // 'completed' | 'in-progress' | 'locked'
        currentWeek: 1,
        weeks: [
          { id: 'w1', label: 'Week 1', duration: '30mins', status: 'done' },
          { id: 'w2', label: 'Week 2', duration: '30mins', status: 'done' },
          { id: 'w3', label: 'Week 3', duration: '30mins', status: 'done' },
        ],
      },
      {
        id: 't2',
        title: 'Kitchen Essentials and Basic Food Preparation',
        lessonsCount: 8,
        status: 'in-progress',
        currentWeek: 2,
        weeks: [
          { id: 'w1', label: 'Week 1', duration: '30mins', status: 'done' },
          { id: 'w2', label: 'Week 2', duration: '30mins', status: 'now' },
          { id: 'w3', label: 'Week 3', duration: '30mins', status: 'locked' },
        ],
      },
      {
        id: 't3',
        title: 'Macro Perspective to Tourism and Hospitality',
        lessonsCount: 6,
        status: 'locked',
        currentWeek: 0,
        weeks: [
          { id: 'w1', label: 'Week 1', duration: '30mins', status: 'locked' },
          { id: 'w2', label: 'Week 2', duration: '30mins', status: 'locked' },
        ],
      },
    ],
  },
  {
    id: 'y2',
    year: '2nd Year',
    subjectName: 'Advanced Hospitality Track',
    topics: [
      {
        id: 't4',
        title: 'Front Office Operations',
        lessonsCount: 5,
        status: 'locked',
        currentWeek: 0,
        weeks: [
          { id: 'w1', label: 'Week 1', duration: '30mins', status: 'locked' },
        ],
      },
    ],
  },
];

// Looks up a topic + week pair by their IDs across all subject groups.
// Used by the Lesson/AI Mentor chat page to build its breadcrumb and title.
export function findTopicAndWeek(topicId, weekId) {
  for (const group of mockSubjectGroups) {
    const topic = group.topics.find((t) => t.id === topicId);
    if (topic) {
      const week = topic.weeks.find((w) => w.id === weekId);
      return { topic, week };
    }
  }
  return { topic: null, week: null };
}

// Finds the student's "current" in-progress topic + week — used by the
// generic "Start quiz" buttons (Dashboard, Badges, Profile, etc.) that
// aren't tied to a specific lesson. Falls back to the first topic/week
// found if nothing is "in-progress".
export function findCurrentTopicAndWeek() {
  for (const group of mockSubjectGroups) {
    const topic = group.topics.find((t) => t.status === 'in-progress');
    if (topic) {
      const week = topic.weeks.find((w) => w.status === 'now') || topic.weeks[0];
      return { topic, week };
    }
  }
  // Fallback: first topic/week in the whole dataset
  const firstGroup = mockSubjectGroups[0];
  const firstTopic = firstGroup?.topics[0];
  const firstWeek = firstTopic?.weeks[0];
  return { topic: firstTopic ?? null, week: firstWeek ?? null };
}