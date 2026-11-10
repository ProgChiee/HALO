# Student login greeting rollout

This migration has NOT been applied to production. Keep Hibernate ddl-auto=validate.

Before rollout, back up the database and verify restoration in a disposable environment. Check information_schema.columns for user_entity.has_successful_login; do not rerun the versioned migration if it already exists. Record the user row count. Schedule a brief maintenance window: MariaDB DDL locking/duration depends on server version and table size and has not been measured here.

Apply V20261110_01__student_successful_login_marker.sql only with separate approval, before deploying the new backend. DDL may implicitly commit; application transaction rollback is not a DDL recovery plan. Verify unchanged row count, column nullability/default, and that all existing rows have NULL before reopening writes. No audit-based backfill is performed because authentication audits are best-effort.

NULL means legacy/unknown, FALSE means a newly registered Student with no successful login, TRUE means a successful login has committed. Registration sets FALSE explicitly; imports and legacy creation paths default safely to NULL. The existing locked token-issuance transaction captures firstLogin (true/false/null), marks the Student TRUE and signs the token. If issuance or commit fails, no successful response is returned and the marker rolls back. Success means committed token issuance, not guaranteed receipt over a network; a lost response followed by a new login is a subsequent login.

Deploy backend then frontend. Existing clients ignore the additive nullable response field. Old tokens/sessions without the field receive a neutral greeting. Session storage only caches the backend classification; it never decides from account age or local visit counts. The signed token also captures the classification. This presentation metadata never grants authorization. Preview/Support use neutral target greetings and do not call normal login issuance.

Validation queries (read-only):

```sql
SELECT COUNT(*) FROM user_entity;
SELECT COLUMN_NAME, IS_NULLABLE, COLUMN_DEFAULT
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'user_entity'
  AND column_name = 'has_successful_login';
SELECT has_successful_login, COUNT(*) FROM user_entity GROUP BY has_successful_login;
```

Rollback: roll back application binaries and leave the nullable column/data in place. This is compatible with the old mapping. Only if permanent removal is separately approved, export the marker values keyed by account ID and then use ALTER TABLE user_entity DROP COLUMN has_successful_login. Dropping it destroys greeting history and must not be part of routine rollback. Restoring a whole backup after new academic writes could lose those writes and is not recommended.

Isolated H2 tests validate additive SQL, preserved NULL values and transaction behavior. Native MariaDB DDL/locking, production deployment and connected browser verification require staging checks; H2 results are not native MariaDB verification.
