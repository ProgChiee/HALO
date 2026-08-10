// Mock data for the Professor's Subject Management page.
//
// TODO: once the backend is ready, replace with real API calls (suggested
// endpoints in professorService.js). This mirrors the shape the Student
// side's Subjects page eventually needs to consume — keep subject/week
// field names consistent between the two once connected to a real API.

export const mockManagedSubjects = [
  {
    id: 's1',
    title: 'Front Office Operations',
    weeks: [
      {
        id: 'w1',
        title: 'Week 1: Introduction',
        objectives: [
          'Understand the foundational concepts of hospitality and tourism operations.',
          'Identify the key departments within a front office team.',
        ],
        content: {
          method: 'text',
          fileName: '',
          fileType: '',
          linkUrl: '',
          text: 'The front office is the nerve center of any hotel operation...',
        },
        video: {
          title: 'Front Office Operations: A Complete Guide',
          source: 'Hospitality Pro',
          duration: '14:23',
          url: '',
        },
      },
      {
        id: 'w2',
        title: 'Week 2: Systems',
        objectives: ['Explain how property management systems support front office work.'],
        content: { method: 'text', fileName: '', fileType: '', linkUrl: '', text: '' },
        video: { title: '', source: '', duration: '', url: '' },
      },
      {
        id: 'w3',
        title: 'Week 3: Check-In Procedures',
        objectives: [
          'Understand the foundational concepts of hospitality and tourism operations.',
          'Understand the foundational concepts of hospitality and tourism operations.',
          'Understand the foundational concepts of hospitality and tourism operations.',
        ],
        content: {
          method: 'text',
          fileName: '',
          fileType: '',
          linkUrl: '',
          text: 'The front office is the nerve center of any hotel operation. It serves as the primary point of contact between guests and the property, handling reservations, check-ins, guest services, and check-outs...',
        },
        video: {
          title: 'Front Office Operations: A Complete Guide',
          source: 'Hospitality Pro',
          duration: '14:23',
          url: '',
        },
      },
    ],
  },
  {
    id: 's2',
    title: 'Housekeeping Operations',
    weeks: [
      {
        id: 'w1',
        title: 'Week 1: Room Standards',
        objectives: [],
        content: { method: 'text', fileName: '', fileType: '', linkUrl: '', text: '' },
        video: { title: '', source: '', duration: '', url: '' },
      },
    ],
  },
  {
    id: 's3',
    title: 'Food and Beverage Services',
    weeks: [],
  },
];