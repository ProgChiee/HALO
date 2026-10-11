# HALO navigation consistency update

## Files changed

- `src/components/shared/Navigation.module.css`
- `src/pages/admin/acting/ActingMode.module.css`
- `src/pages/superadmin/styles/Admins.module.css`
- `src/pages/superadmin/styles/SuperAdminDashboard.module.css`
- `src/pages/superadmin/styles/SuperAdminProfile.module.css`
- `src/pages/student/styles/Badges.module.css`
- `src/pages/student/styles/LessonChat.module.css`
- `src/pages/student/styles/Profile.module.css`
- `src/pages/student/styles/Progress.module.css`
- `src/pages/student/styles/Quiz.module.css`
- `src/pages/student/styles/StudentDashboard.module.css`
- `src/pages/student/styles/Subjects.module.css`
- `src/pages/professor/styles/ProfessorDashboard.module.css`
- `src/pages/professor/styles/ProfessorProfile.module.css`
- `src/pages/professor/styles/StudentProgress.module.css`
- `src/pages/professor/styles/SubjectManagement.module.css`
- `src/pages/admin/styles/AdminDashboard.module.css`
- `src/pages/admin/styles/AdminProfile.module.css`
- `src/pages/admin/styles/Monitoring.module.css`
- `src/pages/admin/styles/ProfessorManagement.module.css`
- `src/pages/admin/styles/StudentManagement.module.css`
- `src/pages/admin/styles/SubjectManagement.module.css`
- `src/components/shared/Sidebar.module.css`
- `src/components/shared/PageShell.module.css`
- `src/components/shared/Navbar.module.css`
- `src/pages/admin/acting/ProfessorMode.module.css`
- `src/pages/admin/acting/StudentMode.module.css`

## Changes

Shared Navigation CSS centralizes topbar surfaces, 64px minimum height (expands for wrapping), breadcrumbs, 20px navigation icons, and existing page-header alignment. Pages retain padding, actions, content, and responsive rules. Sidebar remains 260px on desktop with consistent 44px-minimum navigation items, soft-green active/hover state, visible focus, and safe long-name wrapping. Mobile bar/drawer header use white surfaces and matching controls.

Professor and Student acting modes now compose one shared stylesheet for banners, targets, Change Account/Exit controls, hover/focus states, and responsive wrapping. Selected-target emphasis uses existing primary tokens.

## Preserved

No token or UIPrimitives changes. No JavaScript, menu destinations, routes, role checks, session logic, drawer focus/closing behavior, or logout changes. Existing 1024px drawer, 768px compact padding, and 600px acting-banner breakpoints remain. Auth pages retain their existing navigation and content rather than adding a new topbar. Specialized lesson editor controls retain their arrangement; shared sidebar/drawer styling applies.

## Verification

- Frontend: 148 tests passed, 0 failed.
- Lint: passed.
- Production build: passed.
- Live browser visual verification not performed.
