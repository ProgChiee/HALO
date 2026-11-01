# Super Admin preview / mode-session recovery fix

## Root cause and evidence

Two defects in the inspected implementation explain how a valid backend mode could become inaccessible in the UI:

1. The Professor and Admin scoped API adapters treated every HTTP 403 as an invalid mode and navigated back to selection. A resource-level permission denial does not necessarily invalidate the session. The backend session remained active, correctly rejecting subsequent creation with ACTING_MODE_ALREADY_ACTIVE. The new resource-denial regression verifies that a valid Professor Preview keeps its interface and exit control.
2. Mode selection had no server-backed active-mode discovery or recovery. If creation succeeded but the response was lost, or the frontend rejected the response/navigation failed, the selector could not identify or exit the mode. The new lost-response regression reproduces this state and verifies recovery.

The precise first failing request in the owner's manual browser reproduction was not captured. Therefore these are confirmed synchronization/recovery defects, not proof that a specific HTTP 403 caused that original browser incident. Successful preview creation, separate-request restoration, and exit were verified with isolated H2, and selector-to-preview navigation with component/router tests. Connected browser reproduction remains NOT VERIFIED.

## Changes

- Professor/Admin adapters now revalidate the owned session after resource-level 403/404 responses. Valid sessions retain their current interface and return control; confirmed invalid sessions still fail closed. Student already used this distinction.
- Super Admin-only GET /api/admin/acting/active returns owned active sessions with explicit preview/acting type and selected target. It validates actor identity, session ownership, target status/role, token versions, and expiry.
- Super Admin-only DELETE /api/admin/acting/active/{id} ends the owned mode with audit attribution. Preview revocation and occupancy removal occur after transaction commit. Existing preview handlers reject the revoked capability thereafter. Acting recovery uses the existing owned transactional revocation operation.
- The Super Admin dashboard and mode pages show an Active mode recovery banner with the actual role/target, Resume active mode, and Return to Super Admin. Conflicts and failed entry requests refresh server state. No persistent client active-mode flag is trusted.
- Return waits for the exit API. A failed exit keeps the active mode visible with actionable feedback. Successful exit returns to the original Super Admin dashboard.
- Professor/Student entry failures with a known session ID attempt the matching cleanup endpoint. Lost responses and failed cleanup remain recoverable through discovery. On a nested-mode conflict, confirmation dialogs close to reveal recovery controls. Other validation errors retain their existing dialogs.
- Admin restoration network failures also expose recovery controls.

## Security preserved

Nested Super Admin modes remain prohibited server-side. Recovery cannot access another actor's sessions, and regular Admin/Professor/Student callers cannot use the new recovery endpoints. Original identity and selected target stay separate. No role or JWT is replaced. Preview capabilities still cannot authorize real acting writes. Existing regular Admin workflows, Admin Mode navigation restrictions, expiry checks, target scope, enrollment protections, and audit attribution remain intact.

## Files changed in this fix

Backend:
- HALO/src/main/java/com/ptc/halo/service/AdminActingSessionService.java
- HALO/src/main/java/com/ptc/halo/controller/AdminActingController.java
- HALO/src/test/java/com/ptc/halo/SuperAdminModeTest.java
- HALO/src/test/java/com/ptc/halo/SuperAdminModeHttpTest.java

Frontend:
- src/pages/admin/acting/ActiveModeRecovery.jsx (new)
- src/pages/admin/acting/AdminMode.jsx
- src/pages/admin/acting/ProfessorMode.jsx
- src/pages/admin/acting/StudentMode.jsx
- src/components/shared/PageShell.jsx
- src/services/admin/adminModeService.js
- src/services/admin/professorModeService.js
- src/services/apiClient.js
- tests/superadmin-modes.test.mjs

This report is new. Prior working-tree changes from enrollment/reporting and the initial Super Admin modes implementation were preserved.

## Executed verification

| Check | Result |
| --- | --- |
| Focused backend mode/preview suite | 56 passed, 0 failed/errors/skipped (before the additional recovery HTTP test) |
| Complete backend suite and compilation | 812 total, 801 passed, 0 failures, 0 errors, 11 intentional skips |
| Final focused frontend mode suites | 38 passed, 0 failed/skipped |
| Complete frontend suite | 383 passed, 0 failed/skipped |
| Final lint | Passed, no warnings/errors |
| Final production build | Passed |

Backend verification used the existing isolated H2 test copy. The 11 skips are existing opt-in MariaDB and optional Mentor cases. Tests cover owner-only discovery/recovery, original identity, nested rejection, separate preview transactions, revoked preview rejection, another mode after exit, role gates, selector navigation, refresh restoration, scoped requests, valid-session resource denial, lost creation responses, failed exit/retry, failed creation, and existing regular Admin mode behavior.

The first frontend run caught regressions caused by discovery requests on Settings and closing non-conflict Student confirmation errors. Both were corrected without weakening the existing assertions; the complete suite subsequently passed. Parallel Vite test servers emitted HMR-port warnings; there were no test failures from them.

## Limitations / deployment notes

- Browser tooling was attempted: the browser bridge was unavailable and native Chrome access timed out awaiting app approval. No browser visual or connected E2E success is claimed. The temporary browser fixture/server was removed/stopped.
- The exact initial failing browser request still needs confirmation with network evidence during the next manual check. Recheck Professor Preview, Student Preview, refresh, Return, and entry to a different mode.
- Preview storage remains process-local and ephemeral, as before. Multi-instance preview coordination is not implemented.
- Native MariaDB concurrency was not verified. No live database records/schema, migrations, permanent roles, or academic data were changed. No commits or pushes were made.
- Deploy frontend and backend together: the recovery banner requires the new authenticated discovery/exit endpoints.
