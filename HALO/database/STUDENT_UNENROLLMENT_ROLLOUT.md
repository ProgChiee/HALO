# Student subject unenrollment — deployment and recovery plan

Status: application implementation and isolated verification only. **Live migration and deployment are not approved.** Do not start this backend against the old schema. Keep `spring.jpa.hibernate.ddl-auto=validate`.

## Exact additive migration

Apply `V20261029_01__student_enrollment_deactivation.sql` once, manually, after the original `student-subject-enrollment.sql`:

```sql
ALTER TABLE student_subject_enrollments ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE student_subject_enrollments ADD COLUMN unenrolled_at_epoch_ms BIGINT NULL;
```

Existing enrollment rows receive `active=true` and a NULL unenrollment timestamp. No row is deleted or recreated. Primary keys, Student/Subject foreign keys, `(student_id, subject_id)` uniqueness, `recorded_at_epoch_ms`, and `source` remain unchanged. New enrollment constructors explicitly set active=true. This script is not wired into application startup.

MariaDB DDL implicitly commits. The two statements are not an atomic transaction. If interrupted after the first statement, inspect the columns and apply only the missing second statement; do not rerun blindly or remove the successfully added column. No `IF NOT EXISTS` is used to hide schema drift.

## Before requesting live execution

1. Confirm the actual running backend's resolved DB host, port and database without exposing credentials. Repository defaults and older recovery ports are not proof of the runtime connection. Use the verified port for all subsequent commands.
2. Schedule a maintenance window. Stop HALO backend instances, background writers and frontend access. Do not permit old and new backend versions to serve traffic simultaneously.
3. Confirm InnoDB is healthy and `innodb_force_recovery=0`. Confirm a restore procedure on a separate disposable instance; never start the corrupted original data directory.
4. Inspect the following SELECT-only checks and record their results securely:

```sql
SELECT DATABASE(), @@port, VERSION(), @@innodb_force_recovery;
SELECT ENGINE FROM information_schema.TABLES
 WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='student_subject_enrollments';
SELECT COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT
 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
 AND TABLE_NAME='student_subject_enrollments' ORDER BY ORDINAL_POSITION;
SELECT INDEX_NAME,NON_UNIQUE,COLUMN_NAME,SEQ_IN_INDEX
 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()
 AND TABLE_NAME='student_subject_enrollments' ORDER BY INDEX_NAME,SEQ_IN_INDEX;
SELECT COUNT(*) AS enrollment_rows FROM student_subject_enrollments;
SELECT COUNT(*) AS duplicate_pairs FROM (
 SELECT student_id,subject_id FROM student_subject_enrollments
 GROUP BY student_id,subject_id HAVING COUNT(*)>1
) duplicates;
SELECT COUNT(*) AS orphaned_enrollments FROM student_subject_enrollments e
 LEFT JOIN user_entity u ON u.id=e.student_id LEFT JOIN subjects s ON s.id=e.subject_id
 WHERE u.id IS NULL OR s.id IS NULL;
```

Verify both new columns are absent, and inspect existing triggers if any. A partially applied or differently named schema requires reconciliation before proceeding. Preserve a secured export of the five original enrollment columns for exact comparison; do not publish user records in logs.

## Backup and restore instructions (manual, pending approval)

Use XAMPP's matching MariaDB utilities, a new timestamped backup filename, the verified port and an authorized database user. `--password` prompts interactively; never put a password in a command or report.

```text
C:\xampp\mysql\bin\mysqldump.exe --host=127.0.0.1 --port=<VERIFIED_PORT> --user=<AUTHORIZED_USER> --password --single-transaction --quick --routines --triggers --events --hex-blob --result-file="<NEW_ABSOLUTE_BACKUP_FILE.sql>" halo_db
```

Check the process exit status, nonzero file size and `Get-FileHash -Algorithm SHA256 <backup-file>`. Keep this backup separate from previous recovery backups. Verify restoration into a **separate disposable MariaDB instance/database**, then compare table counts, constraints and representative history. Never overwrite the only backup.

For recovery, keep traffic stopped and obtain explicit recovery approval. Restore the verified backup into a separately prepared database using the matching `mysql.exe` client and its `source <backup-file>` command, validate it, then switch to it only under an approved deployment plan. Do not restore over active `halo_db`. Restoring a pre-migration backup would discard later legitimate writes, so it is not a routine rollback once traffic has resumed.

## Deployment order and validation

1. Finish isolated regression tests, obtain separate live migration approval and verify the backup/restore rehearsal.
2. Stop traffic and all backend writers; take the final backup.
3. Apply the two ALTER statements manually to the verified database. Hibernate must never apply this migration.
4. Run these read-only checks before restarting:

```sql
SELECT COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT
 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
 AND TABLE_NAME='student_subject_enrollments'
 AND COLUMN_NAME IN ('active','unenrolled_at_epoch_ms');
SELECT COUNT(*) AS rows_after,
 COALESCE(SUM(active=TRUE),0) AS active_rows,
 COALESCE(SUM(active=FALSE),0) AS inactive_rows,
 COALESCE(SUM(unenrolled_at_epoch_ms IS NOT NULL),0) AS unenrollment_timestamps
 FROM student_subject_enrollments;
SELECT CONSTRAINT_NAME,COLUMN_NAME,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME
 FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE()
 AND TABLE_NAME='student_subject_enrollments';
```

`rows_after` must match the pre-migration count; initially `active_rows=rows_after`, `inactive_rows=0`, and `unenrollment_timestamps=0`. Compare the five original columns against the secured pre-migration export. Check unrelated academic table counts and constraints against the backup.

5. Deploy the new backend with `ddl-auto=validate`; confirm validation succeeds. Deploy the matching frontend. Only then reopen traffic. A development Vite server may hot-reload changed source files: do not use the new UI with the old/unmigrated backend.
6. With separate authorization and designated test data, verify live My/Browse behavior, deactivation, old-session rejection, history preservation, re-enrollment and concurrent access. No such live writes have been performed for this implementation.

Expected downtime: the complete stop/backup/DDL/start/validation window. No trustworthy numeric estimate is available without a restore rehearsal and measurement on the deployment storage. The ALTER operations can take a metadata lock or rebuild the table; do not promise zero downtime.

## Rollback limitations

Prefer a forward fix, retaining both new columns. Before any deactivation has occurred, an approved rollback to the old application can leave the additive columns in place after verifying the old application tolerates them. **After any deactivation, the old backend is unsafe:** it checks row existence and would restore access to inactive enrollments. Do not drop the columns, set every enrollment active, or roll back to that backend. Keep service unavailable until a compatible fix/recovery is approved. Preserve unenrollment audit events and post-migration data.

## Application contract and historical behavior

- `DELETE /api/student/subjects/{subjectId}/enrollment` is authenticated STUDENT-only. It deactivates the caller's row, returns `{ "enrolled": false }`, and never accepts a target Student ID. An already inactive owned enrollment succeeds without changing its timestamp or adding another audit event. Missing/unowned enrollment returns 404; invalid IDs return 400. Inactive accounts, revoked credentials and other roles remain denied. Acting headers are rejected.
- The existing POST enrollment endpoint rechecks current eligibility and reactivates the same row. Original identity/provenance is preserved; the current unenrollment timestamp is cleared. Existing ACCOUNT audit events record enroll, unenroll and re-enroll transitions. No duplicate row can bypass the unique constraint.
- My Subjects/Browse Subjects and subject summaries use only active enrollment IDs. Inactive enrollments can be browsed/re-enrolled only if still eligible. A Student may leave their own enrollment even if their profile/year eligibility has since changed.
- Protected lesson, Mentor and assessment access checks require active enrollment, including old session/attempt IDs. Read-only curriculum preview remains available through its existing eligibility contract.
- Completion, grades, answers, attempt limits, badges and Mentor conversations are preserved. Re-enrollment restores permitted access to the original data; it does not restart a completed quiz or unlock an otherwise unavailable module.
- Dashboard historical totals/latest score and study history remain based on preserved records. Active subject cards and Continue Learning shortcuts are refreshed after a successful change and on page navigation. Historical progress summaries are not erased; trying to open protected content still requires active enrollment.
- Preview/Support retain their target Student scope. Neither exposes an unenrollment endpoint or button. Normal Student enrollment events do not invalidate a mode's separate target context.

## Concurrency and boundaries

Enrollment changes, assessment starts/submissions and study writes use the existing database pessimistic Student-row lock. Enrollment transitions then lock the owned enrollment row. Protected access within a write transaction uses a locking, active-only enrollment query and retains that lock until commit. This is a current read on InnoDB, avoiding stale repeatable-read snapshot decisions after waiting for a lock.

Mentor indexing/AI calls remain outside database transactions. The final Mentor transaction takes the Student lock before its existing module lock, then rechecks and locks active enrollment before saving any messages/session. If unenrollment committed while AI was running, that final transaction is rejected. If a protected write acquired its locks first, it commits before unenrollment can succeed. No protected academic write can commit behind a successful unenrollment while that enrollment remains inactive. A later authorized re-enrollment permits future writes again.

Lock ordering is Student, then module where required, then enrollment; no external AI call holds these locks. Existing Support session guards remain in place. Database timeouts/deadlocks can still reject a transaction safely and roll it back; UI retry is explicit and unenrollment is idempotent.

Read-only requests authorized before unenrollment may finish returning already-read content. Data already downloaded cannot be recalled. New requests after commit must check active enrollment. Isolation/concurrency behavior is tested using H2; MariaDB-specific lock contention and interrupted-DDL recovery still require a separate disposable MariaDB rehearsal or approved deployment verification.

## Verification record

Executed on 2026-10-29:

- Backend compilation and 14 relevant test classes: **127 tests, 0 failures, 0 errors, 0 skipped**. Classes: `StudentEnrollmentTest`, `StudentEnrollmentHttpTest`, `StudentEnrollmentMigrationTest`, `StudentLessonAccessTest`, `MentorExternalTransactionTest`, `AssessmentSubmissionAccessTest`, `AdminStudentModeTest`, `AdminStudentPreviewTest`, `StudentDashboardLatestAttemptTest`, `StudentSubjectBulkTest`, `AdminStudentModeHttpTest`, `AdminStudentPreviewHttpTest`, `LessonStudyTest`, `StudentStudyActivityTest`.
- Complete frontend suite: **360 passed, 0 failed, 0 skipped**.
- Frontend ESLint and Vite production build: passed. Build output was directed to a temporary directory.
- Focused frontend coverage includes confirmation/cancel/focus restoration, background isolation, Escape prevention, duplicate submit prevention, failed-request retry, My/Browse/re-enrollment synchronization, dashboard shortcut refresh and mode restrictions.
- Backend concurrency tests use separate transactions, latches and competing threads for study writes, quiz submissions, final Mentor saves and duplicate unenrollment. In-flight mocked AI revocation is also covered.

Automated writes use an isolated temporary H2 schema, never production resources or live accounts. AI calls are mocked. Migration tests execute the exact additive SQL in H2 MariaDB mode and verify existing values, default active state, uniqueness and foreign keys. No live migration, live enrollment mutation or manual browser write test was performed. Native MariaDB migration/locking behavior is not claimed as verified by H2.

## Files changed for this implementation

Backend production (under `HALO/src/main/java/com/ptc/halo/`):

- `entity/StudentSubjectEnrollmentEntity.java`
- `repository/StudentSubjectEnrollmentRepository.java`
- `service/StudentEnrollmentService.java`
- `service/StudentLessonAccessService.java`
- `service/MentorService.java`
- `controller/StudentEnrollmentController.java`

Backend tests (under `HALO/src/test/java/com/ptc/halo/`):

- `StudentEnrollmentTest.java`
- `StudentEnrollmentHttpTest.java`
- `StudentEnrollmentMigrationTest.java`
- `StudentLessonAccessTest.java`
- `MentorExternalTransactionTest.java`

Frontend:

- `src/pages/student/components/EnrollmentSubjects.jsx`
- `src/pages/student/components/UnenrollSubjectDialog.jsx` (new)
- `src/pages/student/components/StudentDashboard.jsx`
- `src/pages/student/styles/EnrollmentSubjects.module.css`
- `src/services/student/studentService.js`
- `src/utils/studentEnrollmentEvents.js` (new)
- `tests/student-enrollment.test.mjs`
- `tests/student-dashboard-score.test.mjs`

Migration/documentation:

- `HALO/database/V20261029_01__student_enrollment_deactivation.sql` (new)
- `HALO/database/STUDENT_UNENROLLMENT_ROLLOUT.md` (new)

No configuration, route definitions, live data, original enrollment migration, quiz scoring or badge rules were changed. Existing working-tree changes were preserved; no commit or push was made.
