# HALO typography update

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
- `src/components/shared/Button.module.css`
- `src/components/shared/ChangePasswordModal.module.css`
- `src/components/shared/Navbar.module.css`
- `src/components/shared/Sidebar.module.css`
- `src/pages/admin/components/Monitoring.jsx`

## Typography

No Hurme font asset or font-face definition was found. The system fallback is -apple-system, BlinkMacSystemFont, Segoe UI, sans-serif. No font files were downloaded; the earlier Google Inter import was removed.

Shared tokens define base size 100%, rem sizes 0.75/0.875/1/1.125/1.25/1.5/1.875, weights 400/500/600/700, and line heights 1.25/1.35/1.625. Legacy aliases resolve to these tokens. Component body text uses 1rem rather than relative percentages to avoid compounding font sizes.

Main titles, section/subsection headings, card titles, form labels/inputs, buttons, tables, quiz, chat, progress, and chart labels now share the scale. Hardcoded CSS sizes/weights/line heights and four 11px chart-axis font sizes were replaced with tokens. Shared styles also cover Super Admin.

## Verification

- Frontend tests: 148 passed, 0 failed.
- Lint: passed.
- Production build: passed.
- Compared color and layout declarations against the pre-typography snapshot: unchanged.
- No live browser visual verification performed.
