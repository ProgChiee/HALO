# Shared UI consistency update

## Files changed

- `src/components/shared/UIPrimitives.module.css`
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
- `src/components/shared/ChangePasswordModal.module.css`
- `src/components/shared/GlassCard.module.css`
- `src/components/shared/Toast.module.css`
- `src/components/shared/Button.module.css`

## Consolidation

UIPrimitives.module.css centralizes control surfaces/focus/disabled/invalid states, labels, card and dialog surfaces, close-icon buttons, table headings, tabs, chips, semantic statuses, and loading/empty/error text. Existing CSS Modules compose these rules while retaining their own layout, sizing, padding, overflow, and responsive declarations.

Shared Button styling now provides consistent alignment, a 44px minimum height, icon sizing, focus treatment, disabled state, and primary/secondary/danger/ghost/text/icon variants. The existing loading implementation and disabled logic are unchanged; spinner contrast is adapted for light variants.

## Intentionally unchanged

- Finalized token definitions.
- Page DOM, routes, API calls, handlers, business logic, and authorization.
- Dialog focus trap, Escape/backdrop handlers, pending locks, and existing header/body/footer arrangement.
- Table columns, scrolling, data states, and responsive layouts.
- Circular avatar/spinner/send controls and meaningful status/progress graphics.
- Native checkbox/radio behavior and existing accessibility semantics.
- Embedded lesson content and specialized objective editors retain their existing structure.

No new card nesting or page/component architecture rewrite was introduced. Shared rules also cover existing Super Admin consumers.

## Verification

- Full frontend suite: 148 passed, 0 failed.
- ESLint: passed.
- Production build (including CSS Modules composition): passed.
- Live browser visual verification not performed.
