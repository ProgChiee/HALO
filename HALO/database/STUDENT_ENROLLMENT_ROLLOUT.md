# Explicit Student subject enrollment rollout

## Repository findings
Subjects currently come from StudentSubjectService.findByYearLevel; StudentLessonAccessService and StudentLearningProgressionService enforce active role, year, publication/generation and week unlocking. Enrollment is not an access prerequisite. Assessment attempts reference assessment versions; passing updates student_module_progress. Badges, lesson_study_days and mentor_sessions/messages are independent historical records. None of these tables or calculations are changed.

## Compatibility phase implemented
- New table records only explicit authenticated enrollments. No startup backfill or automatic enrollment.
- My Subjects shows explicit enrollments that remain academically eligible and have a published/generated module. Browse shows the other eligible published subjects.
- Legacy /student/subjects and equivalent acting routes remain available through a clearly labeled compatibility link. Dashboard, learning access, progress and history retain their existing behavior, including access for students with no enrollment rows.
- Enrollment requires an ACTIVE Student, nonblank existing academic ID/section, valid year, and at least one APPROVED + COMPLETED generated lesson. Existing legacy identifier formats are not invalidated.
- Normal Student writes use immutable authenticated identity, a user-row lock, and a database unique pair. Retries are idempotent. Admin Preview/Support expose reads only.
- This release does NOT turn enrollment into an access gate. A future access-enforcement cutover requires separate approval and verification; no enforcement switch is silently enabled.

## Deployment (not executed)
1. Back up halo_db and verify restore capability. Inspect deployed parent table names/types and current constraints; repository migrations reference user_entity, not a users table.
2. Obtain explicit approval, then apply student-subject-enrollment.sql in a maintenance window. DDL is not transactionally reversible in MariaDB. Validate the unique key, foreign keys, and zero initial rows. Do not redeploy with ddl-auto=create/update.
3. Deploy backend with ddl-auto=validate, then frontend. New code requires the table; before migration the new backend must not replace the working release.
4. Verify normal/preview/support reads and synthetic enrollment on an isolated clone first. Production write smoke tests require separate approval.
5. Run student-enrollment-participation-review.sql read-only. Review counts, orphan/missing profiles, changed years, publication status and source evidence. No live connection or backfill was performed by this task.

## Proposed deterministic historical policy (not applied)
One candidate per immutable Student/Subject pair with at least one lesson-study day, completed progress record, started/submitted assessment attempt (any retained version), or nonblank Student Mentor message. Do not infer enrollment from year matching, badges, session creation alone, or an empty progress row. Keep ineligible/inactive cases on a separate review list, never delete history. After explicit approval of an exact reviewed candidate set, insert only missing pairs with source HISTORICAL_PARTICIPATION and the actual import time in epoch milliseconds; do not fabricate a past enrollment date. Existing SELF_ENROLLED rows must never be overwritten. No backfill write script is shipped until the reviewed policy and candidate set are approved.

## Rollback
Roll back application binaries first; leave the additive table and any enrollments intact. Do not drop a populated table. Export/retain new enrollment records before any later approved schema rollback. Existing access and learning history are independent of this table.

## Recovery update (2026-10-28, reported by operator)
The operator reports that MariaDB was recovered on port 3311, a backup exists, the enrollment migration was applied, the enrollment table contains zero records, and backend startup/schema validation passed. These are operator-confirmed observations, not independent live queries by this task. Do NOT rerun the migration. Connection settings/environment variables remain unchanged by this work.

Verification resumes against a separate H2 workspace with Hibernate ddl-auto=validate. An isolated H2 fixture schema is loaded before validation; no enrollment migration is rerun against MariaDB. The historical candidate query is checked against synthetic history only. Production participation review/backfill and any live enrollment smoke test remain pending explicit approval. Zero enrollment rows are valid: My Subjects is empty, Browse lists eligible published subjects, and the compatibility link preserves existing learning access.

## Verification completed after recovery
- Relevant backend suite: 141 executed, 140 passed, 0 failures, 0 errors, 1 skipped. Compilation passed. Isolated H2 fixture schema with Hibernate validate; no live configuration copied.
- Skipped: ModuleScopedMentorTest.realUploadedTextPdfIndexesWithoutProvider requires the optional halo.test.uploads fixture location. Synthetic module-index tests passed.
- Complete frontend suite: 317 passed, 0 failed, 0 skipped. ESLint and production Vite build passed.
- Covered: explicit lists, year/publication/profile/status eligibility, idempotent concurrent enrollment, database unique constraint, immutable authenticated identity, unauthorized roles, Preview/Support read-only entry points, unchanged snapshots of attempts/quiz versions/progress/Mentor/badges/study days/account identity, and pre-enrollment legacy lesson access.
- UI coverage includes submenu active/expanded states, mobile drawer, original lesson routes, errors/retry, duplicate-submit guard, and enrollment refresh. Existing navigation tests were updated for the two approved child links.
- Read-only participation query now compares completed=TRUE for MariaDB/H2 portability. It was tested on synthetic history only; no production candidates or enrollments were created.
- No migration was rerun, no recovered MariaDB records were accessed or changed, no connection/environment settings were changed, and no live enrollment/AI/email requests were made. Manual browser/live authenticated verification remains pending.
