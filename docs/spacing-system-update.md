# HALO spacing update

## Files changed

- `src/styles/variables.css`
- `src/pages/superadmin/styles/Admins.module.css`
- `src/pages/superadmin/styles/SuperAdminDashboard.module.css`
- `src/pages/superadmin/styles/SuperAdminProfile.module.css`
- `src/pages/superadmin/components/Admins.jsx`
- `src/pages/student/styles/Badges.module.css`
- `src/pages/student/styles/LessonChat.module.css`
- `src/pages/student/styles/Profile.module.css`
- `src/pages/student/styles/Progress.module.css`
- `src/pages/student/styles/Quiz.module.css`
- `src/pages/student/styles/StudentDashboard.module.css`
- `src/pages/student/styles/Subjects.module.css`
- `src/pages/professor/styles/LessonEditor.module.css`
- `src/pages/professor/styles/ProfessorDashboard.module.css`
- `src/pages/professor/styles/ProfessorProfile.module.css`
- `src/pages/professor/styles/StudentProgress.module.css`
- `src/pages/professor/styles/SubjectManagement.module.css`
- `src/pages/professor/components/SubjectManagement.jsx`
- `src/pages/login/styles/ForgotPassword.module.css`
- `src/pages/login/styles/Login.module.css`
- `src/pages/login/styles/Register.module.css`
- `src/pages/admin/styles/AdminDashboard.module.css`
- `src/pages/admin/styles/AdminProfile.module.css`
- `src/pages/admin/styles/Monitoring.module.css`
- `src/pages/admin/styles/ProfessorManagement.module.css`
- `src/pages/admin/styles/StudentManagement.module.css`
- `src/pages/admin/styles/SubjectManagement.module.css`
- `src/pages/admin/components/ProfessorManagement.jsx`
- `src/pages/admin/components/StudentManagement.jsx`
- `src/pages/admin/components/SubjectManagement.jsx`
- `src/pages/admin/acting/ProfessorMode.module.css`
- `src/pages/admin/acting/StudentMode.module.css`
- `src/context/notifications/ToastContext.jsx`
- `src/components/shared/Button.module.css`
- `src/components/shared/ChangePasswordModal.module.css`
- `src/components/shared/Dialog.module.css`
- `src/components/shared/PageShell.module.css`

## Shared scale

variables.css defines space-1/2/3/4/5/6/8/10/12 as 4/8/12/16/20/24/32/40/48px. Existing xs/sm/md/lg/xl/2xl aliases map to the scale; the previous 64px alias now maps to 48px.

## Application

Normalized CSS margins, padding, and gaps across role pages, auth, acting-mode banners, dialogs, and responsive navigation. Primary controls use 12px vertical padding; targeted cards/dialogs use 24px padding; existing 16px form gaps and compact responsive padding remain. Inline empty-state padding and toast gaps now use tokens. Small 2/3px chip padding and 6px gaps were normalized to 4/8px.

The 44px positioned-input-icon inset is represented by a shared calculated token. A -1px border overlap and fluid 3vw clamp term are retained as technical layout exceptions. Table widths, horizontal scrolling, breakpoints, colors, typography, shadows, and radii remain unchanged.

## Verification

- Full frontend tests: 148 passed, 0 failed.
- ESLint: passed.
- Production build: passed.
- Declaration comparison confirmed no changes to protected typography/color/dimension/overflow/shadow/radius rules.
- Live browser visual verification was not performed.
