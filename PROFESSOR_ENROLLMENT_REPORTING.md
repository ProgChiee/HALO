# Professor enrollment reporting

## Semantics preserved

Before this change, the Dashboard student count meant ACTIVE Student accounts whose current academic profile year matched at least one Professor-owned subject. Current/Former monitoring separated ACTIVE from INACTIVE/ARCHIVED accounts. Neither measure represented enrollment. Learning Engagement used the same year-based roster.

Authorization remains subject ownership plus the existing academic-year scope. Enrollment is an additional reporting dimension, not an authorization grant. No enrollment, learning, assessment, badge, or history mutation was added.

## Dashboard contract

- `totalStudents`: unchanged eligible ACTIVE account count, now labeled **Eligible Students**.
- `activelyEnrolledStudents`: distinct eligible ACTIVE accounts with at least one active membership in an owned subject matching their current profile year.
- `eligibleStudentsWithoutActiveEnrollment`: the first count minus the second. Includes both never-enrolled and previously-enrolled eligible accounts.

Membership is counted with EXISTS, so multiple qualifying subjects do not duplicate a Student. Existing publication-independent Professor academic scope is retained; this report does not redefine Student learning availability.

## Monitoring and engagement contract

The existing `view=CURRENT|FORMER` parameter retains its account-status meaning. An additive `enrollment=ALL|ACTIVE|NEVER|PREVIOUS` parameter defaults to ALL.

Within the authorized owned-subject/current-year scope:

- ACTIVE: at least one active enrollment.
- PREVIOUS: enrollment history exists, but none is active.
- NEVER: no enrollment history exists.
- ALL: no membership restriction.

ACTIVE takes precedence when active and inactive rows coexist. NEVER means never in the **current authorized scope**, not never anywhere in HALO. A year-level change can remove old memberships from this reporting scope without deleting those records.

Both list APIs return additive `enrollmentStatus` fields. Filtering precedes pagination and totals, with stable user-ID ordering. Historical engagement formulas are unchanged. Detail endpoints retain their original authorization, independently of list filters.

Normal Professor requests derive identity from authentication. Acting and Preview routes use their validated session target and retain their existing restrictions. Default API calls remain compatible.

## Changed files for this task

Backend (under `HALO/src/main/java/com/ptc/halo/`):

- `enums/ProfessorEnrollmentView.java`
- `repository/ProfessorEnrollmentScope.java`
- `repository/ProfessorDashboardRepository.java`
- `repository/UserRepository.java`
- `dtoResponse/ProfessorDashboardResponse.java`
- `dtoResponse/ProfessorStudentSummary.java`
- `service/ProfessorDashboardService.java`
- `service/ProfessorStudentService.java`
- `service/ProfessorEngagementService.java`
- `controller/ProfessorStudentController.java`
- `controller/ProfessorEngagementController.java`
- `controller/AdminProfessorModeController.java`
- `controller/AdminProfessorPreviewController.java`

Frontend:

- `src/utils/professorEnrollment.js`
- `src/pages/professor/components/ProfessorDashboard.jsx`
- `src/pages/professor/components/StudentProgress.jsx`
- `src/pages/professor/components/LearningEngagement.jsx`
- `src/pages/professor/styles/StudentProgress.module.css`

Tests:

- `HALO/src/test/java/com/ptc/halo/ProfessorEnrollmentReportingTest.java`
- `HALO/src/test/java/com/ptc/halo/ProfessorEngagementTest.java`
- `HALO/src/test/java/com/ptc/halo/ProfessorStudentScopeHttpTest.java`
- `HALO/src/test/java/com/ptc/halo/EngagementHttpTest.java`
- `HALO/src/test/java/com/ptc/halo/AdminProfessorPreviewHttpTest.java`
- `tests/professor-dashboard.test.mjs`
- `tests/professor-progress-pagination.test.mjs`
- `tests/professor-diagnostics.test.mjs`
- `tests/professor-navigation.test.mjs`

This document is also new. Other pre-existing working-tree changes are outside this task.

## Rollout and verification boundaries

No schema migration is required. The existing enrollment lifecycle schema, including `active`, is required as before. No live records or schema were modified, and no deployment was performed.

Backend tests use isolated H2 with synthetic fixtures. Native MariaDB query/concurrency behavior and connected browser visual/end-to-end behavior are not verified by those tests. Existing opt-in native-database tests must remain disabled unless an explicitly disposable environment is supplied.

## Verification results

Executed October 30, 2026; final results checked November 1, 2026. The current backend Java source and tests match the isolated tested snapshot by file hash. Lint was also rerun successfully November 1.

| Verification | Result |
| --- | --- |
| Complete backend suite and compilation | 793 total; 782 passed; 0 failures; 0 errors; 11 intentional skips |
| New reporting lifecycle tests | 8 passed; scenarios A-H, scoped pagination, identity and retained activity |
| Complete frontend suite | 375 passed; 0 failed; 0 skipped |
| Frontend lint | Passed |
| Production build | Passed |

The backend skips are nine opt-in MariaDB tests and two optional Mentor fixture tests. No native MariaDB write tests were enabled. Professor scope, enrollment lifecycle, lesson access, HTTP identity, acting/preview, and engagement regression classes all passed in the full suite.

The first frontend run found two outdated assertions expecting the old My Students label and five-column table. These were updated for the deliberate new label and enrollment column while retaining numeric-value and keyboard-scroll-region assertions. The subsequent complete run passed.

Browser visual checks, connected browser E2E, and native MariaDB verification of the new queries remain unperformed. Component tests verify rendering, filtering, pagination resets, and read-only requests; they do not establish visual layout or native database performance. Existing Vite test servers emitted non-fatal HMR port-contention messages. No production source was changed to work around test failures.
