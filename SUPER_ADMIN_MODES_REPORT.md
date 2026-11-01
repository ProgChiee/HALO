# Super Admin role modes

## Implemented contract

The existing JWT remains a Super Admin JWT. No role is changed, no impersonation token is issued, and the original identity remains in the account footer. The existing acting-session entity records the original actor, selected target, target role, token-version snapshots, expiry, and revocation. The legacy `adminUserId` response field now also identifies a Super Admin actor.

Super Admin navigation offers Admin Mode, Professor Mode, and Student Mode. Each requires explicit target selection. Normal login still starts in Super Admin Mode.

- **Admin Mode:** an explicitly selected ACTIVE Admin, confirmed before entry. Reuses the existing Admin dashboard, Professor/Student management, monitoring, profile, and settings. A new allowlisted controller delegates only these Admin operations through a validated session. Account mutations retain existing validation and transactions; their audit events identify the original Super Admin and selected Admin. Target credentials, profile editing, and nested role switching are not exposed. Settings retains appearance controls.
- **Professor Mode:** existing Preview and audited acting workflows, with the selected Professor's academic scope. Super Admin-specific frontend routes and return destination reuse the same workflow components and API adapters.
- **Student Mode:** existing ephemeral Preview and restricted Support workflows. Enrollment, academic access, assessment, and Mentor guards are unchanged. Neither mode gains enrollment-management permissions.

Admin Mode navigation is derived from its context and excludes the Modes group. No nested mode endpoints are exposed by its API adapter/controller. The session service also rejects creating another Super Admin mode while a valid mode exists, even through direct requests without the current mode header. Return to Super Admin revokes the current session before navigation. Normal Admin's Professor/Student modes and return behavior remain unchanged.

## Security and session behavior

- Normal `/api/admin/**`, Professor, and Student endpoints retain their original role gates. Only explicit acting/preview routes accept Super Admin in addition to Admin; every operation still requires a valid owned session and target role.
- New `/api/super-admin/acting/admin/**` endpoints require Super Admin plus `X-Acting-Session`; an Admin-target session cannot authorize Professor/Student operations or vice versa.
- Session IDs are UUIDs, target identities come from the stored session, and client-supplied account identifiers do not replace actor identity.
- Inactive, wrong-role, missing-profile Student, and invalid targets fail closed. Super Admin cannot enter a target account requiring an initial password change.
- Actor/target token versions and account status are revalidated. Existing logout increments the original actor's token version, invalidating acting and preview sessions without changing target credentials.
- Concurrent Super Admin session creation locks the actor and uses a locking read of active sessions. This avoids a stale repeatable-read snapshot permitting two real modes.
- Preview credentials remain process-local and never authorize persisted acting endpoints. A process-local occupancy check includes them in the Super Admin nesting guard. Failed preview creation clears its occupancy during transaction rollback.
- Admin audit context is bounded to the synchronous guarded transaction and cleared in `finally`; it changes audit provenance, never authentication or granted authorities.

## Database safety and deployment limits

No schema changes, migrations, live record changes, or deployments were performed. Existing acting-session columns use VARCHAR role values and already reference user accounts, so the inspected migration supports Admin targets and Super Admin actors without alteration.

The existing Preview architecture is single-process and ephemeral: restart ends previews. This implementation retains that limitation. Cross-instance exclusive-mode enforcement for previews requires shared session/occupancy storage before multi-instance deployment; do not treat this as verified multi-node support. Real acting-session exclusion is database-backed.

No connected browser, live academic-write workflow, real AI-provider, or native MariaDB concurrency verification was performed. Isolated H2 and component/HTTP tests do not establish those results.

## Files changed for this task

Backend paths below are relative to `HALO/src/main/java/com/ptc/halo/`:

- `config/SecurityConfig.java`
- `repository/AdminActingSessionRepository.java`
- `service/AdminActingSessionService.java`
- `service/AdminApiException.java`
- `service/AdminProfessorModeService.java`
- `service/AdminProfessorPreviewService.java`
- `service/AdminStudentModeService.java`
- `service/AdminStudentPreviewService.java`
- `service/AdminService.java`
- `service/ActivityLogService.java`
- `service/SuperAdminAdminModeService.java` (new)
- `controller/AdminActingController.java`
- `controller/AdminProfessorModeController.java`
- `controller/AdminProfessorPreviewController.java`
- `controller/AdminStudentModeController.java`
- `controller/AdminStudentPreviewController.java`
- `controller/ProfessorEngagementController.java`
- `controller/StudentStudyCalendarController.java`
- `controller/LessonStudyController.java`
- `controller/StudentEnrollmentController.java`
- `controller/ProfilePhotoController.java`
- `controller/AdminValidationHandler.java`
- `controller/SuperAdminAdminModeController.java` (new)

Frontend:

- `src/context/admin/useAdminWorkflow.js` (new)
- `src/services/admin/adminService.js`
- `src/services/admin/adminModeService.js` (new)
- `src/pages/admin/acting/AdminMode.jsx` (new)
- `src/pages/admin/acting/useModeOwner.js` (new)
- `src/pages/admin/acting/ProfessorMode.jsx`
- `src/pages/admin/acting/StudentMode.jsx`
- `src/pages/admin/components/AdminDashboard.jsx`
- `src/pages/admin/components/AdminProfile.jsx`
- `src/pages/admin/components/ProfessorManagement.jsx`
- `src/pages/admin/components/StudentManagement.jsx`
- `src/pages/admin/components/Monitoring.jsx`
- `src/pages/professor/components/ProfessorPageShell.jsx`
- `src/pages/student/components/StudentPageShell.jsx`
- `src/components/shared/PageShell.jsx`
- `src/components/shared/Settings.jsx`
- `src/data/navigationData.js`
- `src/routes/AppRoutes.jsx`
- `src/utils/adminErrors.js`

Tests:

- `HALO/src/test/java/com/ptc/halo/SuperAdminModeTest.java` (new)
- `HALO/src/test/java/com/ptc/halo/SuperAdminModeHttpTest.java` (new)
- `HALO/src/test/java/com/ptc/halo/AdminActingSessionTest.java`
- `HALO/src/test/java/com/ptc/halo/AdminActingHttpTest.java`
- `HALO/src/test/java/com/ptc/halo/AdminProfessorModeHttpTest.java`
- `HALO/src/test/java/com/ptc/halo/AdminProfessorPreviewHttpTest.java`
- `HALO/src/test/java/com/ptc/halo/AdminStudentModeHttpTest.java`
- `HALO/src/test/java/com/ptc/halo/AdminStudentPreviewHttpTest.java`
- `HALO/src/test/java/com/ptc/halo/AuthenticatedIdentityRegressionTest.java`
- `tests/superadmin-modes.test.mjs` (new)
- `tests/admin-navigation.test.mjs`

This report is new. Other working-tree changes from earlier tasks were preserved.
## Verification results — November 1, 2026

| Check | Result |
| --- | --- |
| Complete backend suite and compilation | 809 total; 798 passed; 0 failures; 0 errors; 11 intentional skips |
| Complete frontend suite | 379 passed; 0 failed; 0 skipped |
| Frontend lint | Passed |
| Production build | Passed |
| Focused original/new mode backend run | 53 passed before additional HTTP and concurrency coverage |
| Final concurrent entry regression | Passed in `AdminActingSessionTest` (12 tests in class) |
| New Super Admin service regressions | 11 passed |
| New Super Admin HTTP regressions | 4 passed |
| New frontend mode regressions | 4 passed, including Professor/Student Preview and acting variants |

The 11 backend skips remain nine opt-in MariaDB cases and two optional Mentor fixture cases. No live database write tests were enabled.

Regressions verify explicit target confirmation, original actor preservation after email reuse, target ownership, target-role mismatch, invalid and expired sessions, token-version invalidation, nested real/preview rejection, concurrent real-mode creation, delegated account mutation audit provenance, refresh restoration, return navigation, logout confirmation, and the unchanged regular Admin workflows.

Two failures found during development were corrected: the new Admin endpoint's missing session header now returns 400 rather than 500, and Super Admin navigation retains Dashboard first. Prior tests that explicitly denied Super Admin all acting access were updated for the approved role extension; unauthorized Student/Professor cases remain, alongside new Super Admin-specific negative coverage. No security check was removed simply to make a test pass.

No commits or pushes were made. Native MariaDB concurrency and connected browser visual/E2E verification remain unperformed. Preview occupancy is process-local, matching existing Preview storage; invalidated target previews release their occupancy on the next mode-entry check. Cross-instance preview coordination is not implemented.
