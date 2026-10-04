# Super Admin Priority 2 changes

Scope: SA-09, SA-10, SA-11, SA-12, SA-15, SA-08 and SA-17 only.

## Confirmed original behavior

Admin request DTOs had no Bean Validation; emails were saved without consistent normalization and no database unique index existed. Create/update/status persisted accounts and audit entries without an outer service transaction. Status PATCH flipped the existing status. Monitoring requested all logs filtered to ADMIN actors, excluding Super Admin account-management actions. Admin monitoring also issued two activity queries per Admin. Unexpected business errors lacked safe HTTP status mapping.

## Implementation

- SA-09: create/update require a trimmed name (1?100 characters) and normalized valid email (up to 254). Creation requires a nonblank password of 12?72 characters, also limited to 72 UTF-8 bytes for BCrypt. IDs must be positive; status must be ACTIVE or INACTIVE. Change-password validates nonblank current password and a new password of 8?72 characters/at most 72 UTF-8 bytes, preserving the existing minimum. No password-reset/bootstrap/JWT logic changed.
- SA-10: DTO/entity setters normalize new email writes with trim + lowercase. Repository duplicate lookup compares lower(trim(email)) and excludes the edited account. The generated email_normalized column and unique constraint protect concurrent writes, including callers bypassing the application check. Known constraint failures map to EMAIL_ALREADY_EXISTS / 409. Existing stored email values are not rewritten.
- SA-11: createAdmin, updateAdmin and changeAdminStatus use service-level @Transactional. Account saveAndFlush and audit insertion share the transaction. Exceptions propagate and roll back the account. Audit actions use target ID/name to stay within the existing 255-character action column even for long valid emails.
- SA-12: PATCH requires an explicit desired status. Repeated ACTIVE/INACTIVE requests preserve that state. Each successful request is audited; no mutation is automatically retried by the frontend.
- SA-15: scoped controller advice maps validation to 400, missing Admin to 404, duplicate email to 409 and unexpected failures to safe 500. Authentication/access exceptions are rethrown to existing security handlers. Activity errors retain ACTIVITY_LOGS_UNAVAILABLE / 500. Client messages use known safe codes/field names, never arbitrary SQL/exception text.
- SA-08: caller remains SUPER_ADMIN. Actor filter includes all existing ADMIN activity plus SUPER_ADMIN ACCOUNT activity (create/update/status).
- SA-17: database pagination, default size 20/max 100, createdAt DESC then id DESC. A to-one user entity graph prevents per-row actor fetches; read-only transaction retained. Search checks action/actor name case-insensitively; category filter is server-side. An ordering index supports newest-first reads. Admin activity summaries use one grouped aggregate query instead of two queries per Admin. Dashboard requests only the first ten logs.

## API contract changes

- POST /api/super-admin/create-admin: unchanged body keys name/email/password, stricter validation.
- PUT /api/super-admin/admin/{id}: unchanged body keys name/email, stricter validation.
- PATCH /api/super-admin/admin/{id}/status: body now required: {"status":"ACTIVE"} or {"status":"INACTIVE"}. Empty/toggle requests return 400. BLOCKED is not an activation/deactivation action.
- GET /api/super-admin/activity-logs?page=0&size=20&search=&activityType=ACCOUNT: now a page object, including content, number, size, totalElements and totalPages. Page numbers are zero-based; invalid sizes return 400. Omit activityType for all permitted categories. Dashboard uses page=0&size=10.
- GET /api/super-admin/admins/monitoring: response unchanged; aggregate query optimized.
- POST /api/auth/change-password: same route/body/security; Bean Validation added only to this request DTO/binding.

Monitoring now has Previous/Next controls, server filtering, bounded search length, error/retry states, and suppresses stale page rows while new parameters load. Admin-role monitoring retains its existing API behavior. The existing Admin management UI has create/status controls but no edit form; the existing edit API is covered by backend tests. No Delete feature was added.

## Database migration ? already applied locally on 2026-10-03

Inspected configured localhost:3307/halo_db (MariaDB 10.4.32) before modifying schema:

- user_entity had only its primary index, no unique email index.
- zero duplicate normalized-email groups.
- three stored emails were not fully normalized; their values remain unchanged.
- activity_log_entity had primary/user foreign-key indexes, no created_at ordering index.

Applied and verified:

1. HALO/database/super-admin-email-uniqueness.sql: generated virtual email_normalized = LOWER(TRIM(email)), UNIQUE uk_user_email_normalized.
2. HALO/database/super-admin-activity-index.sql: idx_activity_created_id(created_at,id).

No account row, password, role or token version was changed by these migrations. Do not rerun the ALTER on this local database. On another database, inspect existing equivalent indexes and duplicate normalized emails first, then apply the missing migration once. If duplicates exist, stop and resolve them explicitly; do not delete/merge automatically. These SQL files are manual migrations, not Flyway/Liquibase migrations. In particular, Hibernate ddl-auto alone does not install the generated email constraint on a newly created database.

## Files changed in this task

Backend paths relative to HALO/:

- pom.xml (H2 test dependency)
- src/main/java/com/ptc/halo/controller/SuperAdminController.java
- src/main/java/com/ptc/halo/controller/SuperAdminExceptionHandler.java (new)
- src/main/java/com/ptc/halo/controller/AuthController.java (change-password @Valid only)
- src/main/java/com/ptc/halo/controller/PasswordChangeValidationHandler.java (new)
- src/main/java/com/ptc/halo/dtoRequest/AdminRequest.java
- src/main/java/com/ptc/halo/dtoRequest/UpdateAdminRequest.java
- src/main/java/com/ptc/halo/dtoRequest/AdminStatusRequest.java (new)
- src/main/java/com/ptc/halo/dtoRequest/ChangePasswordRequest.java
- src/main/java/com/ptc/halo/entity/UserEntity.java (email setter only in this task)
- src/main/java/com/ptc/halo/entity/ActivityLogEntity.java (ordering index metadata)
- src/main/java/com/ptc/halo/repository/UserRepository.java
- src/main/java/com/ptc/halo/repository/ActivityLogRepository.java
- src/main/java/com/ptc/halo/service/SuperAdminService.java
- src/main/java/com/ptc/halo/service/SuperAdminApiException.java (new)
- src/main/java/com/ptc/halo/service/ActivityLogService.java
- database/super-admin-email-uniqueness.sql (new)
- database/super-admin-activity-index.sql (new)
- src/test/java/com/ptc/halo/SuperAdminActivityHttpTest.java
- src/test/java/com/ptc/halo/SuperAdminDataIntegrityTest.java (new)
- src/test/java/com/ptc/halo/PasswordChangeValidationHttpTest.java (new)

Frontend paths:

- src/services/superadmin/superadminService.js
- src/pages/superadmin/components/Admins.jsx
- src/pages/superadmin/components/SuperAdminDashboard.jsx
- src/pages/admin/components/Monitoring.jsx
- src/components/shared/ChangePasswordModal.jsx (matching input limits)
- src/utils/apiErrors.js
- tests/api.test.mjs
- docs/superadmin-priority2.md (this report)

Other pre-existing workspace changes, including AI and Priority 1 changes, were preserved.

## Verification

- Backend: Maven compile + all isolated tests via -Dtest=*,!HaloApplicationTests test: BUILD SUCCESS; 71 tests, 70 passed, one optional real-PDF fixture skipped, zero failures/errors.
- Excluded HaloApplicationTests: it starts the full application against the configured live database, including startup runners. No live bootstrap/reset was run.
- New H2/JPA tests use real repositories/transactions: normalized duplicates, simultaneous duplicate inserts, account rollback on audit failure for create/update/status, repeated desired states, Super Admin action visibility, category/search/pagination, timestamp-tie ordering, size cap and aggregated monitoring counts.
- HTTP tests: validation 400, positive IDs, explicit status body, normalization, 404/409/safe500, preserved 401/403, page metadata/limits and password-change validation.
- Frontend: npm test ? 33 passed; npm run lint ? passed; npm run build ? passed.
- Existing security and authentication regression tests passed. No live Gemini request was made.
- Schema indexes were verified on local MariaDB; concurrent-write and rollback tests used isolated H2, not production accounts.

## Restart and manual verification

Restart the HALO backend in IntelliJ from this workspace and refresh the frontend (deploy frontend/backend together because status and pagination contracts changed). Keep existing JWT/password environment settings.

Live browser verification remains pending; no existing user's credentials were used or reset. After restart, sign in as Super Admin and check:

1. Create an Admin with a valid name/email and 12+ character password.
2. Submit the same email in different case/with outer spaces; expect 409 and the specific duplicate message.
3. Activate/deactivate; retrying the same desired status leaves it unchanged.
4. Confirm create/update/status audit entries appear in Monitoring and recent dashboard activity.
5. Search/filter and move between pages; verify proper empty/error/retry states.
6. Use the existing PUT API if testing Admin edits; there is no edit UI to exercise.
7. Check that an ADMIN caller still gets 403 and unauthenticated requests still get 401.

Remaining deployment requirement: install the email migration on any other/new database before using account management. No Priority 3/4 work is included.
