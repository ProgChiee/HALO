# HALO visual color update

Styling only; routes, APIs, security, business logic, and responsive layout rules are unchanged.

## Files changed

- `src/styles/global.css`
- `src/styles/variables.css`
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
- `src/pages/student/components/Progress.jsx`
- `src/pages/professor/styles/LessonEditor.module.css`
- `src/pages/professor/styles/ProfessorDashboard.module.css`
- `src/pages/professor/styles/ProfessorProfile.module.css`
- `src/pages/professor/styles/StudentProgress.module.css`
- `src/pages/professor/styles/SubjectManagement.module.css`
- `src/pages/login/styles/ForgotPassword.module.css`
- `src/pages/login/styles/Login.module.css`
- `src/pages/login/styles/Register.module.css`
- `src/pages/admin/styles/AdminDashboard.module.css`
- `src/pages/admin/styles/AdminProfile.module.css`
- `src/pages/admin/styles/Monitoring.module.css`
- `src/pages/admin/styles/ProfessorManagement.module.css`
- `src/pages/admin/styles/StudentManagement.module.css`
- `src/pages/admin/styles/SubjectManagement.module.css`
- `src/pages/admin/components/Monitoring.jsx`
- `src/pages/admin/acting/ProfessorMode.module.css`
- `src/pages/admin/acting/StudentMode.module.css`
- `src/components/shared/Button.module.css`
- `src/components/shared/ChangePasswordModal.module.css`
- `src/components/shared/Dialog.module.css`
- `src/components/shared/GlassCard.module.css`
- `src/components/shared/Navbar.module.css`
- `src/components/shared/Sidebar.module.css`
- `src/components/shared/Toast.module.css`

## Palette and styling

Shared tokens in variables.css contain the requested palette, solid surface aliases, semantic soft backgrounds, readable status-text variants, overlay and shadow tokens. Existing spacing, typography, and breakpoints are retained. Legacy hardcoded CSS/JSX colors were replaced with tokens, including Monitoring charts and the Progress ring. Auth backgrounds now use the off-white page surface; gradients, backdrop blur, and glowing borders were removed. Shared styles also give Super Admin a consistent palette. Original logo assets are unchanged.

## Verification

- Full frontend tests: 148 passed, 0 failed.
- ESLint: passed.
- Production build: passed.
- Live browser/device visual verification: not performed.
