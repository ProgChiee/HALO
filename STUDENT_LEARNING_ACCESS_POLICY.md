# Student learning access policy

This change supersedes the enrollment rollout's year-level-only learning-access compatibility policy.

- Normal Students need an actual student_subject_enrollments row for the authenticated account and the subject owning the requested module, in addition to the existing ACTIVE status, role, academic year, publication, generation, and week-unlocking checks.
- The same target-Student enrollment requirement applies to Admin Student Preview and Support. There is no Admin bypass. Existing owned-session checks, temporary Preview data, and Support audit identity remain in force.
- Lesson content, Mentor creation/messages/history/request reconciliation, assessments and submissions, and lesson-study/progress writes use the shared module authorization check. Old session and attempt IDs cannot waive enrollment.
- The legacy /subjects listing remains read-only. An additive `enrolled` response field comes from actual enrollment rows. Unenrolled subjects show enrollment guidance instead of fetching protected weeks. Published Curriculum Preview remains available through its metadata-only eligible-subject endpoint.
- Existing progress, attempts, answers, scores, badges, study days, conversations, and audit records are retained. Own summary/history data does not grant content access. No enrollment backfill, migration, or automatic data repair is included.
- Deployment requires the existing enrollment table and matching backend/frontend versions. Accounts without enrollment will be blocked from learning actions until they use the existing authorized enrollment flow. No live records are modified by this implementation.

## Root cause and implementation

StudentLessonAccessService.validateSubject() previously treated matching academic year as enrollment. StudentLearningProgressionService.validateModuleAccess() propagated that legacy permission to published lessons, Mentor session creation and access, request reconciliation, assessments, and lesson study writes. The legacy frontend listing also fetched weeks without distinguishing real enrollment.

The shared check now additionally requires existsByStudent_IdAndSubject_Id(authenticatedStudentId, resourceSubjectId). The resource subject is taken from the stored module/week relationship. Failure returns HTTP 403 with ENROLLMENT_REQUIRED. Existing active-account, role, profile/year, publication, generation, progression, ownership, and mode-session checks still apply. Detailed completed-attempt feedback now also checks module access because it includes protected question/answer content. Records and stored scores are retained.

StudentSubjectResponse.enrolled is an additive boolean derived from actual enrollment rows. It controls frontend presentation only. Unenrolled legacy rows display enrollment guidance and a mode-specific Browse Subjects link without requesting protected weeks. Dashboard resume and its Enrolled Subjects section use only enrolled rows. Direct Lesson Chat/Quiz denials offer Browse Subjects. The old rollout notice has been replaced with accurate enrollment guidance.

## Files changed in this task

Backend production:
- HALO/src/main/java/com/ptc/halo/service/StudentLessonAccessService.java
- HALO/src/main/java/com/ptc/halo/service/StudentSubjectService.java
- HALO/src/main/java/com/ptc/halo/service/AssessmentService.java
- HALO/src/main/java/com/ptc/halo/dtoResponse/StudentSubjectResponse.java
- HALO/src/main/java/com/ptc/halo/controller/AdminStudentModeExceptionHandler.java

Frontend production:
- src/pages/student/components/Subjects.jsx
- src/pages/student/components/StudentDashboard.jsx
- src/pages/student/components/EnrollmentSubjects.jsx
- src/pages/student/components/LessonChat.jsx
- src/pages/student/components/Quiz.jsx
- src/utils/lessonErrors.js
- src/utils/quizLoadError.js
- src/utils/studentDiagnostics.js

Backend regression tests:
- HALO/src/test/java/com/ptc/halo/StudentLessonAccessTest.java
- HALO/src/test/java/com/ptc/halo/StudentIncompleteProfileTest.java
- HALO/src/test/java/com/ptc/halo/StudentEnrollmentHttpTest.java
- HALO/src/test/java/com/ptc/halo/StudentEnrollmentTest.java
- HALO/src/test/java/com/ptc/halo/AdminStudentModeTest.java
- HALO/src/test/java/com/ptc/halo/AdminStudentPreviewTest.java
- HALO/src/test/java/com/ptc/halo/AssessmentSubmissionAccessTest.java
- HALO/src/test/java/com/ptc/halo/BadgeAwardRulesTest.java
- HALO/src/test/java/com/ptc/halo/MentorExternalTransactionTest.java
- HALO/src/test/java/com/ptc/halo/StudentSubjectBulkTest.java

Frontend regression tests and contract fixture:
- tests/student-enrollment.test.mjs
- tests/student-diagnostics.test.mjs
- tests/student-dashboard-score.test.mjs
- tests/fixtures/backend-contract.json

Documentation:
- STUDENT_LEARNING_ACCESS_POLICY.md

## Verification

- Relevant backend tests: 113 passed, 0 failed, 0 errors, 0 skipped. Production compilation and test compilation passed. Tests ran in an isolated temporary workspace using H2, synthetic fixtures, mocked AI, and ddl-auto=validate against a synthetic schema. Production application properties were excluded.
- Complete frontend suite: 341 passed, 0 failed, 0 skipped.
- Frontend lint: passed.
- Frontend production build: passed; output written to a temporary directory.
- The initial backend run exposed an outdated query-count ceiling. Explicit enrollment adds one authorization query; the regression still verifies a constant query count as weeks increase, with the bound updated from 5 to 6.
- Synthetic authorized fixtures now explicitly enroll the Student. New tests remove only those isolated enrollments to verify old session IDs, quiz submissions, study tracking, completed history, and Admin mode access cannot bypass the rule.
- Direct MockMvc requests with authenticated test principals return 403 ENROLLMENT_REQUIRED for protected lessons, Mentor open/send/history/request lookup, assessment read/start/status/result/submission, and study writes without enrollment.
- Existing session isolation, Preview temporary state, Support audit provenance, quiz/version/history, badge, and submission tests pass.
- Published Curriculum Preview remains available to eligible unenrolled Students and excludes unpublished content.
- No live MariaDB queries, writes, migrations, real email/AI requests, or authenticated browser tests were performed for this task.

## Deployment

Deploy the matching backend and frontend together and restart the backend. The already-approved enrollment table must exist. No new migration or automatic enrollment/data repair is required or included. Students without real enrollments must use the existing enrollment flow before learning activities become available. Historical records remain stored and are not reset by enrollment enforcement.

## Frontend navigation cleanup (2026-10-29)

The old mixed-subject accordion catalog and enrollment-rollout notice have been removed from the frontend. Plain /student/subjects and equivalent Admin Student Preview/Support URLs now replace-redirect to their own /subjects/my page. Bookmarked /subjects?subject=<ID> links remain supported and render only the selected enrolled subject's weeks/lessons. Invalid IDs never fall back to a catalog. Unenrolled deep links show one enrollment-required state with Browse Subjects outside any lesson accordion, without loading protected weeks.

The existing summary API still supplies the authorized subject lookup; this frontend cleanup does not alter its contract, enrollment checks, historical records, or curriculum preview. General-navigation callers in Dashboard, Progress, lesson/quiz error states, and Student-mode fallback routes now target My Subjects directly. Continue Learning/Review Subject and lesson/quiz routes retain their existing subject identifiers and mode context.

Navigation cleanup verification: complete frontend suite 343 passed, 0 failed, 0 skipped; frontend lint and production build passed. Browser history, selected-subject routes, empty states, read-only Curriculum Preview, mode isolation, accessibility, and mobile navigation are covered by automated tests. Manual browser visual checks and live MariaDB checks were not performed. No backend production code or API contract was changed by the navigation cleanup.

Files changed for the navigation cleanup:
- src/pages/student/components/Subjects.jsx
- src/pages/student/styles/Subjects.module.css
- src/pages/student/components/EnrollmentSubjects.jsx
- src/pages/student/components/StudentDashboard.jsx
- src/pages/student/components/Progress.jsx
- src/pages/student/components/Quiz.jsx
- src/pages/student/components/LessonChat.jsx
- src/routes/AppRoutes.jsx
- tests/student-enrollment.test.mjs
- tests/student-diagnostics.test.mjs
- tests/student-incomplete-profile.test.mjs
- tests/student-navigation.test.mjs
- STUDENT_LEARNING_ACCESS_POLICY.md
