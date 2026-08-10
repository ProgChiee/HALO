// SHARED subjects catalog — single source of truth for both:
//   - Professor's Subject Management (full CRUD: create/edit/delete
//     subjects, add/edit weeks and their lesson content)
//   - Student's Subjects page (read-only view of what's available)
//
// Because both professorService.js and studentService.js read/write
// through the SAME shared store (services/shared/subjectsStore.js), any
// change a professor makes is immediately visible to the student-facing
// service too. This mirrors how a real backend would work: one subjects
// table, read/written by different roles with different permissions.
//
// TODO: once the backend is ready, this whole file goes away — both
// services will fetch from the same API endpoints instead.

export const mockSubjectsCatalog = [
  // ===== 1st Year =====
  {
    id: 's101',
    title: 'Introduction to Quick Food Service',
    year: '1st Year',
    weeks: [
      { id: 'w1', title: 'Week 1: Introduction', objectives: [], content: { method: 'text', fileName: '', fileType: '', linkUrl: '', text: '' }, video: { title: '', source: '', duration: '', url: '' } },
    ],
  },
  {
    id: 's102',
    title: 'Kitchen Essentials and Basic Food Preparation',
    year: '1st Year',
    weeks: [],
  },
  {
    id: 's103',
    title: 'Macro Perspective to Tourism and Hospitality',
    year: '1st Year',
    weeks: [],
  },
  {
    id: 's104',
    title: 'Recreation and Leisure Management',
    year: '1st Year',
    weeks: [],
  },
  {
    id: 's105',
    title: 'Risk Management as Applied to Food Safety, Security and Sanitation',
    year: '1st Year',
    weeks: [],
  },
  {
    id: 's106',
    title: 'Philippine Regional Cuisine',
    year: '1st Year',
    weeks: [],
  },
  {
    id: 's107',
    title: 'Food Processing Technology',
    year: '1st Year',
    weeks: [],
  },
  {
    id: 's108',
    title: 'Food and Beverage Services',
    year: '1st Year',
    weeks: [],
  },
  {
    id: 's109',
    title: 'Fundamentals of Lodging Operations',
    year: '1st Year',
    weeks: [],
  },
  {
    id: 's110',
    title: 'Introduction to Management, Incentives, Conferences and Event Management',
    year: '1st Year',
    weeks: [],
  },

  // ===== 2nd Year =====
  {
    id: 's201',
    title: 'Entrepreneurship in Tourism and Hospitality',
    year: '2nd Year',
    weeks: [],
  },
  {
    id: 's202',
    title: 'Bar and Beverage Management',
    year: '2nd Year',
    weeks: [],
  },
  {
    id: 's203',
    title: 'International Cuisine',
    year: '2nd Year',
    weeks: [],
  },
  {
    id: 's204',
    title: 'Catering Service Management',
    year: '2nd Year',
    weeks: [],
  },
  {
    id: 's205',
    title: 'Front Office Operations',
    year: '2nd Year',
    weeks: [
      {
        id: 'w1',
        title: 'Week 1: Introduction',
        objectives: [
          'Understand the foundational concepts of hospitality and tourism operations.',
          'Identify the key departments within a front office team.',
        ],
        content: {
          method: 'text', fileName: '', fileType: '', linkUrl: '',
          text: 'The front office is the nerve center of any hotel operation...',
        },
        video: { title: 'Front Office Operations: A Complete Guide', source: 'Hospitality Pro', duration: '14:23', url: '' },
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
          method: 'text', fileName: '', fileType: '', linkUrl: '',
          text: 'The front office is the nerve center of any hotel operation. It serves as the primary point of contact between guests and the property, handling reservations, check-ins, guest services, and check-outs...',
        },
        video: { title: 'Front Office Operations: A Complete Guide', source: 'Hospitality Pro', duration: '14:23', url: '' },
      },
    ],
  },
  {
    id: 's206',
    title: 'Housekeeping Operations',
    year: '2nd Year',
    weeks: [
      { id: 'w1', title: 'Week 1: Room Standards', objectives: [], content: { method: 'text', fileName: '', fileType: '', linkUrl: '', text: '' }, video: { title: '', source: '', duration: '', url: '' } },
    ],
  },
  {
    id: 's207',
    title: 'Bread and Pastry Production',
    year: '2nd Year',
    weeks: [],
  },
];