# HALO page layout update

## Files changed

- `src/components/shared/PageLayout.module.css`
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
- `src/pages/admin/styles/AdminDashboard.module.css`
- `src/pages/admin/styles/AdminProfile.module.css`
- `src/pages/admin/styles/Monitoring.module.css`
- `src/pages/admin/styles/ProfessorManagement.module.css`
- `src/pages/admin/styles/StudentManagement.module.css`
- `src/pages/admin/styles/SubjectManagement.module.css`

## Layout strategy

Shared centered containers use maximum widths of 1280px for regular content, 1440px for dashboards/management/monitoring, 880px for lesson editing and quizzes, and 720px for profiles. Existing page padding and responsive rules are retained.

Metric grids use bounded 220px minimum tracks and shared gaps; secondary panels use bounded 360px minimum tracks. Toolbar controls wrap. Badge tracks use a bounded 280px minimum. Existing page-specific responsive overrides remain.

Lesson Chat conversation and composer share a 1120px container, messages cap at 72ch, and published reading content caps at 80ch. Quiz actions wrap and answer content can break long text. Existing table column definitions, Professor five-column layout, minimum table widths, and local horizontal scrolling are unchanged.

## Intentionally unchanged

Auth forms already have narrow form widths; modal forms and their field grouping/scroll/focus behavior remain intact. Subject/week accordions remain lists, avoiding a structural redesign. Acting modes inherit reused Professor/Student page layouts; mode selection and navigation are unchanged. No DOM or JavaScript changes. No finalized tokens, shared UI primitives, or navigation styles changed.

## Verification

- Frontend: 148 tests passed, 0 failed.
- Lint: passed.
- Production build: passed.
- Compared typography/color/border/shadow declarations against the pre-change snapshot: unchanged.
- Live browser visual verification not performed.
