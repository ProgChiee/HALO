# Student academic profile recovery (no migration)

Read-only verification on 2026-10-23 identified ACTIVE Student user IDs **26, 31, 32** without `student_profile_entity` rows. No live records were modified.

The earlier registration implementation in commit `8d6ba37` saved the account and academic profile separately without a service transaction. A failure between saves could leave an account without a profile. Current `AuthService.registerStudent` validates both sets of fields and creates both rows in one transaction. The live user table has no creation/import metadata that proves how these particular accounts originated. Do not infer their academic details from names, emails or neighboring records.

## Recovery procedure — authorized Admin only

1. Obtain and verify each Student's actual Student ID, section and year level from authorized institutional records. Do not fabricate values or delete/re-register the account.
2. After deployment and explicit approval to repair live data, use the existing authenticated **PUT `/api/admin/students/{userId}`** endpoint, with its existing JSON fields: `name`, `studentId`, `section`, `yearLevel`. Supply the verified current name for compatibility with the existing request contract. When creating a missing profile the service preserves the existing account name, email, password, role, status and token version.
3. New profile IDs must follow the existing new-registration rule: two digits + `AHRT` + `-` + four digits. Section is required (at most 50 characters); year level must be an existing `FIRST_YEAR` or `SECOND_YEAR` enum value. If an official legacy ID needs a different format, obtain a separate approved policy decision; do not invent a replacement ID to bypass validation. Existing stored IDs are not reformatted.
4. Only the existing ADMIN permission authorizes this endpoint. Students, Professors and Super Admins retain their current lack of access to this Admin route. Do not use a Preview/Support route for recovery. This does not introduce an Admin Create Student UI or a Student self-service academic edit endpoint.
5. Recovery locks the existing user row, checks target role, validates academic inputs, enforces Student ID uniqueness and creates the profile plus account audit entry in one transaction. Failed validation or persistence rolls back without changing learning records. Existing unique indexes on `student_id` and `user_id` provide database integrity protection.
6. Verify via GET `/api/admin/students/{userId}` that `profileIncomplete` is false. The Student can click **Check again** on Profile or Subjects; their subjects use the verified year level under the existing eligibility rules. Historical progress remains linked to the original user ID.

The Student Profile page remains readable with safe placeholders while incomplete. Subjects returns HTTP 409 `PROFILE_INCOMPLETE` when no profile/year level exists, with contact-administrator guidance. Direct lesson access remains subject to the existing enrollment checks. Preview/Support entry continues requiring a complete profile.

No schema migration is required. This document is a recovery plan, not authorization to execute live repairs. The Admin management UI currently has no academic edit form; an authorized operator must use the existing API after verification and approval. No live recovery, email, password reset or account mutation was performed during this fix.

## Files changed for Student Audit Issue #2

Backend (paths relative to `HALO/src/main/java/com/ptc/halo/`):
- `service/ProfileService.java`
- `service/StudentSubjectService.java`
- `service/StudentRegistrationValidator.java`
- `service/AdminService.java`
- `service/AdminApiException.java`
- `dtoResponse/StudentProfileResponse.java`
- `controller/StudentLessonExceptionHandler.java`
- `controller/AdminValidationHandler.java`

Frontend (paths relative to the frontend project):
- `src/pages/student/components/Profile.jsx`
- `src/pages/student/components/Subjects.jsx`
- `src/pages/student/components/AcademicProfileNotice.jsx`
- `src/pages/student/styles/AcademicProfileNotice.module.css`

Regression tests:
- `HALO/src/test/java/com/ptc/halo/StudentIncompleteProfileTest.java`
- `HALO/src/test/java/com/ptc/halo/StudentIncompleteProfileHttpTest.java`
- `HALO/src/test/java/com/ptc/halo/StudentProfileRecoveryTest.java`
- `HALO/src/test/java/com/ptc/halo/StudentRegistrationAtomicityTest.java`
- `HALO/src/test/java/com/ptc/halo/AdminValidationHttpTest.java`
- `tests/student-incomplete-profile.test.mjs`

Documentation: `HALO/database/student-profile-recovery.md` (this file).

## Verification

- Backend compile and focused Student/Admin/authentication/profile/security regressions: **286 passed, 0 failed, 8 skipped** (294 discovered). Tests ran against isolated H2 databases in a temporary checkout, with no live configuration copied. The skipped tests were the opt-in Admin monitoring (6), Professor scope (1) and Student dashboard (1) live MariaDB checks.
- Complete frontend suite: **252 passed, 0 failed, 0 skipped**.
- Frontend lint and production build: passed. Parallel Vite test servers emitted non-fatal HMR port-in-use messages; no frontend tests failed.
- Read-only MariaDB session confirmed affected IDs, no partially populated existing profiles, and unique indexes on `student_id` and `user_id`.
- Tests cover safe self-profile retrieval, ignored foreign-user query parameters, typed incomplete-subject errors, direct subject-access denial, Admin-only recovery, validation/uniqueness, preserved identity fields, rollback on audit failure, registration rollback on a database constraint failure, normal Student flows, and read-only Preview rendering.
- Password-reset security implementation files were compared with the previous fix snapshot and are unchanged; their regression tests also passed.
- No live authenticated recovery request, real email delivery or manual browser verification was performed. Live repairs require verified academic records and explicit approval. Recovery must be deployed before use; existing accounts remain incomplete until repaired.
