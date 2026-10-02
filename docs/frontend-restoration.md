# Frontend restoration audit

Source: new-frontend commit d7e0a8e3f18f4f6ebdc792f8fadea4bff98b4593.

Git reflog shows a switch to main/new-backend at 4f03985, followed by a return to new-frontend before this audit. The frontend was already identical to d7e0a8e. No reset, checkout, backend changes, or UI recreation was needed. Existing layouts/styles/navigation are preserved.

Removed unused demo files (no active page imports):

- src/data/student/aiMentorData.js
- src/data/student/badgesData.js
- src/data/student/profileData.js
- src/data/student/progressData.js
- src/data/student/quizData.js
- src/data/student/studentDashboardData.js
- src/data/student/subjectsData.js
- src/data/professor/professorDashboardData.js
- src/data/professor/professorProfileData.js
- src/data/professor/studentProgressData.js
- src/data/professor/subjectsManagementData.js
- src/data/superadmin/adminsData.js
- src/data/superadmin/superadminDashboardData.js
- src/data/superadmin/superadminProfileData.js
- src/data/shared/subjectsCatalog.js
- src/data/admin/adminDashboardData.js
- src/data/admin/adminProfileData.js
- src/data/admin/aiUsageData.js
- src/data/admin/professorsData.js
- src/data/admin/quizPerformanceData.js
- src/data/admin/sessionMonitoringData.js
- src/data/admin/studentsData.js
- src/services/shared/subjectsStore.js
- src/services/shared/progressStore.js


Updated files:
- src/utils/backendContract.js: reject unknown/prototype roles; retain exact backendRole.
- src/utils/session.js: persist uppercase backend role, normalize UI role on refresh, validate matching role/token storage.
- src/context/login/AuthContext.jsx: use the shared session writer.
- src/pages/login/components/Login.jsx: role-specific replace navigation and login error feedback.
- src/services/authService.js: resend OTP is a public request without bearer token.
- vite.config.js: local /api proxy to localhost:8081.
- src/pages/student/components/LessonChat.jsx: remount per module route; cancel stale requests; retain citation line breaks.
- src/services/student/aiMentorService.js: cancellation for message requests.
- tests/frontend.test.mjs and tests/api.test.mjs: refresh, backend-role storage, role-isolated rendering, real-service contracts, and propagated failures.

Real API connections retained from the restoration source (all prefixed /api):
- POST /auth/login; registration, OTP, password reset/change.
- GET /student/subjects and /student/subjects/{subjectId}/weeks.
- GET /student/ai-learning-modules/week/{weekId}; module.id is used for mentor open.
- POST /student/mentor/open/{moduleId}, POST /student/mentor/message/{sessionId}, GET /student/mentor/session/{sessionId}.
- Student dashboard, progress, badges, profile and assessment APIs.
- Professor subjects/weeks, module upload/edit/file upload, generate, approve, decline, dashboard and student progress APIs.
- Admin and super-admin dashboard, accounts, monitoring, activity log and profile APIs.

Backend inspected in actual HALO/: AuthService returns database role; Role contains ADMIN, STUDENT, PROFESSOR, SUPER_ADMIN (no ORGANIZER). MentorService uses ModuleMaterialIndex and appends file/page citations to haloMessage. Spring Security and enrollment checks are unchanged. No backend files were edited.

Verification:
- 20 frontend tests pass, including all four role mappings and all 16 role/guard combinations.
- ESLint passes.
- Vite production build passes.
- No styles/assets/layouts/navigation were replaced; design source remains d7e0a8e.
- Tests use test-only transport doubles; application code has no fake response fallback.

Remaining integration limits:
- No listener detected on local port 8081 during this audit. Live account logins, enrollment/database behavior, and Gemini calls were not exercised. Automated request/role tests are not live end-to-end verification.
- StudentAiLearningController does not populate files in its response, and there is no authorized student file-download endpoint. Source PDFs are indexed server-side for mentor use, and filename/page citations render from actual replies, but student PDF browsing/download cannot be connected without a related backend API addition. No invented URLs or public upload-directory access were added.
- Restart Vite for the proxy. Start the existing HALO backend in IntelliJ, then test real accounts for each role and a published module. Production hosting must proxy /api or set VITE_API_URL including /api.
