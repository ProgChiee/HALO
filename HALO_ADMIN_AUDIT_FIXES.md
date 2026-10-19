# HALO Admin audit remediation

Date: October 19, 2026 (Asia/Manila).
Primary reference: HALO_ADMIN_FINAL_AUDIT.md. Original audit findings are preserved.

## Root causes and fixes

### 1. Logout credential replay — fixed
The server previously wrote only a logout audit event; its 24-hour JWT remained current. AuthService.logout now locks the authenticated account, rechecks the presented JWT against its current token version, increments that version, and commits before returning success. Existing JWT middleware rejects old credentials with HTTP 401. Repeated logout cannot increment the version again using the revoked token. Audit remains best-effort after commit; audit failure does not undo revocation.

This is explicitly **account-wide logout**: all earlier JWTs and acting contexts for that account become invalid. HALO has no separate refresh-token flow. No new revocation table or migration is needed.

The browser previously swallowed a failed logout request and immediately discarded its session. It now waits for server confirmation, retains the dialog/session on network or server failure, prevents duplicate requests, and permits retry. An already-revoked-token response permits safe local cleanup after a lost response. A changed account is not cleared by a stale logout. Successful logout resets the displayed theme to Light without deleting the saved per-account preference.

### 2. Missing Student profiles — fixed without repairing data
Management previously started from StudentProfile rows. Missing rows hid otherwise legitimate accounts; status/archive/restore response construction then failed and rolled back.

Management now starts from STUDENT UserEntity rows with a LEFT JOIN to optional profiles. Existing account-state tabs still select ACTIVE, INACTIVE, or ARCHIVED. The response provides profileIncomplete; the UI shows Profile incomplete and Not set for missing academic fields. View and lifecycle actions work without creating or modifying academic profile data. Role checks, transactional lifecycle changes, prior-state restore, token invalidation, and audit behavior remain in place.

Both Student Preview and Support entry require a complete existing academic profile (Student ID, section, year level). Missing details return HTTP 409 PROFILE_INCOMPLETE before session creation. The UI explains the problem and keeps Support confirmation open. No Student IDs or academic eligibility were invented.

### 3. Search — fixed
Selector queries previously searched only name/email despite advertising ID search. They now also search StudentProfile.studentId and Professor.professorId, preserving ACTIVE-only eligibility, Admin authorization, sorting, and pagination.

Activity Log previously searched actor and raw action text but not the displayed acting target's name. It now searches the target relationship and resolves names for legacy archive/restore/status references in the database predicate. Existing category/role filters and database pagination remain intact. No historical action text or audit relationships were rewritten.

### 4. Failed confirmations — fixed
Management parent handlers caught failures without rejecting, causing the shared lifecycle dialog to treat failure as success and close. Parents now propagate failure; the shared dialog retains the action, displays a safe inline error, unlocks retry/Cancel, and blocks duplicate submissions. Student and Professor Management share this implementation. Logout now follows the same failure-retention principle.

## Live read-only verification

MariaDB localhost:3307, halo_db:

- 13 ACTIVE Students; 2 ARCHIVED Students; no current INACTIVE/BLOCKED Student fixtures.
- 8 Professors, all ACTIVE.
- 3 ACTIVE Students without profiles remain unchanged: user 26 (chie@paterostechnologicalcollege.edu.ph), 31 (edichoso@paterostechnologicalcollege.edu.ph), 32 (dichoso@paterostechnologicalcollege.edu.ph).
- 482 existing activity records at the read-only snapshot.
- The new management query includes all Student accounts, including missing-profile rows, and its ACTIVE population matches SQL.
- Six live integration checks passed, including HTTP 200 dashboard and Student/Professor monitoring responses checked against SQL, missing-profile diagnostics, management population, and Activity Log search compatibility.

Live integration uses server-enforced read-only connections, schema generation disabled, no bootstrap initializer, and a test-only signing key. It tests this checkout's isolated HTTP server, not the deployment identity of an already-running backend. No live logout, lifecycle change, profile repair, or other database mutation was performed.

## Count semantics and permissions

Dashboard Total Professors is deliberately still an all-status count; Professor Monitoring is ACTIVE-only. Both currently equal 8. They will legitimately differ when a Professor becomes inactive/archived. This task did not silently redefine that business metric.

Students have self-registration. Student Management has View and lifecycle UI; an existing backend update endpoint exists, but no evidence confirms that missing Admin Create/Edit UI is an intentional permanent permission policy. No new Create/Edit feature or permission was introduced. Confirm that product decision separately before including it in a demonstration script.

## Files changed for this remediation

Backend production (under HALO/src/main/java/com/ptc/halo):
- dtoResponse/StudentListResponse.java
- repository/StudentProfileRepository.java
- repository/UserRepository.java
- repository/ActivityLogRepository.java
- service/AuthService.java
- service/AdminService.java
- service/AdminActingSessionService.java
- service/AdminApiException.java

Frontend production:
- src/components/shared/AccountLifecycle.jsx
- src/components/shared/LogoutButton.jsx
- src/context/login/AuthContext.jsx
- src/pages/admin/acting/StudentMode.jsx
- src/pages/admin/components/StudentManagement.jsx
- src/pages/admin/components/ProfessorManagement.jsx
- src/pages/admin/styles/StudentManagement.module.css
- src/utils/adminErrors.js

Backend regression tests (under HALO/src/test/java/com/ptc/halo):
- LogoutRevocationTest.java (new)
- AuthenticationAuditTest.java
- AdminActingSessionTest.java
- AdminMonitoringMariaDbLiveTest.java
- AdminProfessorUpdateTransactionTest.java
- AdminProfessorModeTest.java
- AdminStudentModeTest.java
- AdminStudentPreviewTest.java

Frontend regression tests:
- tests/account-lifecycle.test.mjs
- tests/admin-professor-modal.test.mjs
- tests/admin-student-mode.test.mjs
- tests/logout-confirmation.test.mjs

Documentation: HALO_ADMIN_AUDIT_FIXES.md (this report). Pre-existing registration changes were not part of this remediation.

## Migration and history

No database migration. Existing token_version is reused. No account, profile, assessment, progress, Mentor, or audit records were repaired, removed, or rewritten in the live database.

## Verification

- Backend compile and focused Admin/security/profile/audit suite: 180 passed, 0 failed, 0 skipped, including 6 live read-only MariaDB tests.
- Frontend full suite: 219 passed, 0 failed, 0 skipped.
- Frontend lint: passed.
- Frontend production build: passed.
- git diff --check: passed.

Logs: admin-fixes-backend.log, admin-fixes-frontend.log, admin-fixes-lint.log, admin-fixes-build.log.

## Remaining manual verification

Actual browser visual checks, mobile/tablet interaction, live AI/PDF generation, SMTP delivery, and mutating end-to-end workflows remain for system testing using approved test accounts/data. Automated frontend coverage is React/JSDOM, not visual browser certification. Full application context startup against live writable MariaDB was deliberately not run because verification must not mutate live records. Management's existing unpaginated roster still needs production-scale usability testing.

**Final verdict: READY FOR TESTING** for the requested Admin audit fixes. Complete the listed browser and approved test-account acceptance checks before panel demonstration. No panel-demonstration certification is implied by automated results alone.

