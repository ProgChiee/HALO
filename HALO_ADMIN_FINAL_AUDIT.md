# HALO Admin final audit

Date: October 19, 2026 (Asia/Manila). Verdict: **NEEDS FIXES FIRST**.

This was a source review, existing automated regression execution, and read-only live MariaDB verification. No production code, schema, or live records were changed. Test fixtures use isolated test databases. The live integration test starts an isolated localhost HTTP server with production monitoring/security components, a test-only signing key, no bootstrap initializer, no schema generation, and server-enforced read-only database connections. It does not prove that an already-running production backend is deployed from this checkout.

## Confirmed findings and priority

### P1 / High: Logout does not invalidate the authenticated credential
- AuthService.logout (line 49) calls only AuthAuditService.logout; the latter records a deduplicated audit event.
- AuthContext clears browser state and resets the displayed theme, but there is no server-side token revocation/version update on logout.
- JwtService (lines 26-36) issues 24-hour JWTs and checks their stored token version. That version remains unchanged on logout.
- A previously copied Admin bearer token therefore remains usable until expiry or an independent token-version change. An existing acting session can also remain usable with its captured credentials until its own expiry/revocation. This is a source-confirmed security gap; no replay against a live account was performed.
- Recommendation: define and implement server-side logout revocation (per-token revocation or explicit all-session version invalidation), with replay regression coverage. Preserve audit deduplication and saved theme preference.

### P1 / High: Three ACTIVE Students cannot be managed through Student Management
- Live ACTIVE Student users without StudentProfileEntity: 26 (archietalidong), 31 (Dichoso, Eisen), 32 (Dichoso, Eisen).
- AdminService.viewAllStudents (line 258) starts from studentProfileRepository.findAll(), hiding these users. Dashboard and monitoring correctly start from ACTIVE user accounts.
- Result: 13 ACTIVE Students in monitoring versus 10 visible ACTIVE Student profile rows in management.
- viewStudentById, updateStudent, and changeStudentStatus require an existing profile; archiveOrRestore ends by calling viewStudentById inside its transaction. A direct lifecycle request for a profile-less Student fails and rolls back rather than providing a usable account-management response.
- Acting target selection includes these ACTIVE accounts, but Student Preview/Support require a profile, producing a dead end when selected.
- Their existing assessment/progress history count is zero in this snapshot; this is not authorization to delete or invent their academic details.
- Recommendation: make management account-based with optional profile data, allow safe account lifecycle operations independently of academic-profile completeness, and provide explicit incomplete-profile handling for mode entry. Investigate/repair actual profile details separately with authorization.

### P2 / Medium: Mode selector promises ID search but does not implement it
- ProfessorMode.jsx and StudentMode.jsx advertise name, email, or ID search.
- UserRepository.findActingTargets (lines 18-27) searches only user name and email. Neither institutional Student ID nor Professor ID nor numeric user ID participates.
- Recommendation: add scoped ID search or accurately label the supported search fields. Keep ACTIVE-only selection and existing permissions.

### P2 / Medium: Activity Log cannot search all human-readable target names
- ActivityDisplay resolves target names for display. ActivityLogRepository.findAdminLogs (lines 28-38) searches raw action, actor name/email, and authDisplay, but not actingTarget.name/email or the computed display text.
- Live read-only query found 93 acting/preview records whose target's display name appears in none of those searched columns. Searching that name misses those records despite the readable display mentioning it.
- Recommendation: include the safe target/snapshot fields in backend search, with pagination/filter regressions. Do not rewrite historical records.

### P3 / Low: Lifecycle confirmation closes after a failed API request
- StudentManagement.handleToggleStatus and ProfessorManagement.handleToggleStatus catch errors and show a toast without propagating failure.
- AccountLifecycle.confirm (line 24) then treats the resolved callback as success and closes the modal.
- The row is not falsely updated, and a failure toast exists, but retry context is lost. This is a source-confirmed interaction issue, not observed in a rendered browser.
- Recommendation: return an explicit success result or propagate failure, retain the dialog and show a retryable inline error.

## Working features and live data

| Area | Evidence / result |
|---|---|
| Dashboard | Real repository counts. Live HTTP 200; Active Students = 13, Total Professors = 8, active users = 25, inactive users = 0, subjects = 12, modules = 14. |
| Professor Management | Create/View/Edit and lifecycle APIs exist; normalized email/Professor ID conflict checks, password bounds, transactional Professor updates, safe error handling, confirmation/focus behavior have passing regressions. |
| Account lifecycle | Deactivate and Archive are separate. Previous ACTIVE/INACTIVE state is stored for Restore. Token versions increment on state changes. No Student/Professor permanent-delete endpoint in current management flow. Current archived records have valid restore states. |
| Student monitoring | Live HTTP 200; all 13 ACTIVE accounts included via LEFT JOIN, including 3 incomplete profiles; 2 archived Students excluded. 2 scored ACTIVE Students, average latest score 50%, 2 passed attempts, 1 below-60 support Student. Ranking remains separate from support threshold. |
| Professor monitoring | Live HTTP 200; 8 ACTIVE accounts, 110 MODULE activity events attributed to those Professor actors. No incomplete ACTIVE Professor profiles currently found. Every page/row/summary checked against independent SQL. |
| History | Snapshot: 8 attempts, 3 module-progress rows, 96 Mentor messages, 482 activity logs. Read-only verification performed no lifecycle mutations or data repairs. Automated lifecycle tests verify preservation using fixtures. |
| Activity logs | 35 AUTH, 303 ACCOUNT, 116 MODULE, 18 ASSESSMENT, 7 BADGE, 3 PROGRESS records. Category filters, readable descriptions, null/deleted-user fallback, audit transactions, and authentication privacy have passing tests. Search limitation noted above. |
| Professor Preview | Separate process-local credentials; no durable real-acting session authorizer entry. Write routes rejected; target academic scope and Admin ownership validated. |
| Student Preview | Temporary quiz/chat state, server-side scoring, shared grounded Mentor flow, no real Student academic writes. Cross-session/target access and stale requests covered by regressions. |
| Real acting/support | Explicit frontend confirmations, Admin identity retained, target role/scope validation, Admin + target + session audit provenance, session ownership/token-version/expiry checks, exit/revocation behavior tested. |
| Profile | Authenticated self-name update; shared photo state, validation/storage and safe fallback; acting target photo/profile read-only; password and forced-password protections covered. |
| Settings/theme | Per-account Light/Dark/System preference, Admin theme retained in acting modes, Auth pages Light, logout displayed-theme reset covered by frontend tests. |
| Navigation/UI | Shared sidebar, role routes, accessible dialogs, keyboard focus/Escape, responsive drawer logic and local table scrolling covered by DOM-based tests. No actual browser visual certification. |

## Incomplete functionality and design/data decisions

- Student Management currently has View and lifecycle actions, but no Create or Edit UI. The backend exposes Student update, while creation is Student self-registration. Treat Admin Student Create/Edit as unavailable, not as a passed demonstration scenario. Confirm whether these actions are actually required before adding features.
- The Dashboard Professor count is all-status countByRole(PROFESSOR), whereas Professor Monitoring is ACTIVE-only. Currently all 8 Professors are ACTIVE, so live totals agree. Once a Professor is archived/inactive, these numbers diverge. It is labeled Total Professors, so clarify total-vs-current semantics rather than silently changing the business definition.
- Management lists fetch all profile records and filter/search client-side; they have no server pagination. Monitoring/logs are paginated. Load behavior for a large production roster remains unverified.
- Four subjects have no Professor owner. They are excluded from Professor-owned scopes. Determine whether these are intentionally retained legacy subjects; no automatic assignment or deletion is recommended.
- No current INACTIVE/BLOCKED Student/Professor fixtures exist in live MariaDB. Their exclusion and restoration behavior passed isolated regression tests; no live lifecycle transitions were performed.
- Preview state is process-local and expires after 30 minutes. Backend restarts discard previews; multi-instance/load-balanced deployment requires explicit consideration.
- Professor module-activity totals count logs authored by the Professor, not Admin-authored support actions for that Professor. Audit records retain those Admin actions separately. Confirm this metric definition for the panel.

## Verification and limitations

- Backend: **172 tests passed, 0 failed, 0 errors, 0 skipped**; compilation/build succeeded. Selected Admin*, activity, authentication, self-profile, photo and password-validation suites.
- Live MariaDB suite: **5 passed** within the 172; Dashboard, Professor Monitoring and Student Monitoring returned HTTP 200 on the isolated test server. Queries and connections were read-only, with schema initialization disabled.
- Frontend: **215 tests passed, 0 failed**, full suite, serial execution. Includes Admin and integrated acting/preview, profile, theme, modal and navigation coverage.
- Frontend lint: passed. Production build: passed.
- Logs: admin-final-audit-backend.log, admin-final-audit-frontend.log, admin-final-audit-lint.log, admin-final-audit-build.log.
- Not performed: real-browser desktop/tablet/mobile visual review, screen-reader testing, live account creation/edit/archive/restore/logout, live photo upload, live SMTP/password recovery, or live Gemini/PDF generation. Automated tests use mocks/fixtures for external providers. No penetration-test certification is implied.

## Recommended sequence before panel demonstration

1. Fix server-side logout invalidation and profile-less Student management/mode-entry handling; add targeted security and missing-profile regressions.
2. Resolve the missing Student Edit/Create expectations and clarify operational vs total Professor counts.
3. Correct ID search and readable activity search; retain lifecycle confirmation context on failures.
4. Run the same automated checks, then execute a browser-based Admin demo checklist using designated test accounts/data with explicit authorization for mutations. Cover all four modes, exit/re-entry, expiry, Light/Dark, mobile drawer, error paths, profile/photo, and actual provider integrations.

**Final verdict: NEEDS FIXES FIRST.** Core flows and current monitoring are substantially covered and passing, but green tests do not cover the confirmed security and management gaps above.
