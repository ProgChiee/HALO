# HALO radius and shadow update

## Files changed

- `src/styles/variables.css`
- `src/pages/superadmin/styles/Admins.module.css`
- `src/pages/superadmin/styles/SuperAdminDashboard.module.css`
- `src/pages/student/styles/Badges.module.css`
- `src/pages/student/styles/LessonChat.module.css`
- `src/pages/student/styles/Progress.module.css`
- `src/pages/student/styles/Quiz.module.css`
- `src/pages/student/styles/StudentDashboard.module.css`
- `src/pages/student/styles/Subjects.module.css`
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
- `src/pages/admin/acting/ProfessorMode.module.css`
- `src/pages/admin/acting/StudentMode.module.css`
- `src/components/shared/Button.module.css`
- `src/components/shared/ChangePasswordModal.module.css`
- `src/components/shared/GlassCard.module.css`
- `src/components/shared/Navbar.module.css`
- `src/components/shared/Sidebar.module.css`
- `src/components/shared/Toast.module.css`

## Tokens

Radius sm/md/lg/xl/pill: 8/12/16/20/999px. Stat-card radius aliases md.

Shadow sm: 0 1px 2px rgba(15,23,42,0.06). Shadow md: 0 4px 12px rgba(15,23,42,0.08). Shadow lg: 0 10px 24px rgba(15,23,42,0.10). Legacy card/dialog shadow aliases resolve to sm/lg.

## Application

Inputs use sm, shared buttons and tabs md, cards md/lg, modals xl. Chips retain pill rounding; circular avatars, spinners, and icon controls retain 50% geometry. One-off 12px and .5rem radii were replaced with tokens. Regular card surfaces use sm shadows; auth forms, toast, and chart tooltip use md; modals use lg. Removed the shared button hover lift. No broad shadow was added to controls or table rows.

## Verification

- Frontend suite: 148 passed, 0 failed.
- Lint: passed.
- Production build: passed.
- Protected color, typography, spacing, size, and overflow declarations unchanged.
- No backend or business logic changes.
- Live browser visual verification not performed.
