// Mock data for the Professor Management page.
//
// TODO: once the backend is ready, replace with real API calls (suggested
// endpoints in adminService.js) — ProfessorManagement.jsx won't need to
// change shape-wise as long as each professor object keeps these fields:
// id, fullName, email, professorId, subjectsCount, lessonsCount,
// lastLogin, status.

export const mockProfessors = [
  {
    id: 1,
    fullName: 'Dr. Maria Santos',
    email: 'm.santos@halo.edu',
    professorId: 'PROF-2026-014',
    subjectsCount: 4,
    lessonsCount: 22,
    uploadedMaterialsCount: 4,
    lastLogin: 'Today, 9:14 AM',
    status: 'active',
  },
  {
    id: 2,
    fullName: 'Prof. Ramon Dela Cruz',
    email: 'r.delacruz@halo.edu',
    professorId: 'PROF-2026-009',
    subjectsCount: 3,
    lessonsCount: 15,
    uploadedMaterialsCount: 3,
    lastLogin: 'Yesterday, 4:02 PM',
    status: 'active',
  },
  {
    id: 3,
    fullName: 'Prof. Andrea Lim',
    email: 'a.lim@halo.edu',
    professorId: 'PROF-2025-031',
    subjectsCount: 2,
    lessonsCount: 9,
    uploadedMaterialsCount: 2,
    lastLogin: '3 days ago',
    status: 'inactive',
  },
];