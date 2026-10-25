# HALO AD-3 implementation report

Scope: authenticated request identity resolution only. Date: October 25, 2026 (Asia/Manila).

## Root cause and fix
JWT validation authenticated an immutable user ID, but downstream request handlers looked up Authentication.getName() as an email. Concurrent email changes/reuse could substitute another account for profile ownership or acting/audit identity.

AuthenticatedAccount now resolves only CustomUserDetails.getUserId(), rejects email-only principals, checks the returned ID, ACTIVE status, token-version snapshot and authenticated role, and fails closed when the account is missing. CustomUserDetails captures ID/token version at construction so a managed entity refresh cannot change the verified credential identity/version.

The same replacement covers the Admin, Super Admin, Professor and Student request paths found by the source audit. Existing endpoint roles, academic scope, acting-session ownership and preview guards remain in place. Logout and authenticated password changes lock by ID; password changes revalidate status/version/role. Login/logout audit attribution uses ID rather than re-resolving email. Public login, anonymous password recovery and historical display searches retain their intentional email lookups.

No API contract, frontend, schema or migration changes. No live database reads or writes were needed for this implementation. No commits or pushes.

## Verification
- Focused security/integration run: 118 passed, zero failures/errors/skips.
- Complete backend suite including HaloApplicationTests.contextLoads: 710 total; 699 passed, 0 failed, 0 errors, 11 skipped.
- New AuthenticatedIdentityRegressionTest: 25 passed. These use a real JWT/filter and a latch-controlled scheduling point after authentication, with synthetic accounts and mocked repositories. Covers email reuse, profile reads, self-name updates, photo reads/uploads, acting actor/audit ownership, cross-role denial, missing/disabled/role-changed/revoked accounts, logout and password-change identity.
- Existing H2 database concurrency tests for SA-2, AD-1 and AD-2 pass, alongside the complete role/security/regression suite.
- Backend compilation passed. Changed Java sources match the isolated tested copy.
- Frontend tests/lint/build not rerun: no frontend files changed.
- Eleven skips: nine opt-in live MariaDB checks and two document-fixture-dependent Mentor checks. No live MariaDB concurrency, live account mutations, browser workflows, email delivery or paid AI calls were exercised.

## Deployment and limitations
Rebuild and restart the backend. No database migration or data repair is required. JWT format is unchanged, so otherwise-valid existing immutable-ID tokens remain usable. Existing NULL-status and missing-profile records were not repaired.

Tests use isolated H2 and synthetic/mocked accounts; they do not certify deployment-specific MariaDB locking or browser behavior. The change prevents account substitution; it does not introduce a new global cancellation mechanism for work already executing when logout occurs.

## Changed files
Production files primarily contain replacement of email lookups with the shared resolver. Existing test changes replace email-only/string principals and email-keyed mocks with realistic immutable-ID principals, retain assertions, and distinguish revoked credentials from expired preview sessions.
- [HALO/src/main/java/com/ptc/halo/controller/AdminController.java](HALO/src/main/java/com/ptc/halo/controller/AdminController.java)
- [HALO/src/main/java/com/ptc/halo/controller/AiLearningModuleController.java](HALO/src/main/java/com/ptc/halo/controller/AiLearningModuleController.java)
- [HALO/src/main/java/com/ptc/halo/controller/AuthController.java](HALO/src/main/java/com/ptc/halo/controller/AuthController.java)
- [HALO/src/main/java/com/ptc/halo/controller/LessonRevisionController.java](HALO/src/main/java/com/ptc/halo/controller/LessonRevisionController.java)
- [HALO/src/main/java/com/ptc/halo/controller/ProfessorAcademicController.java](HALO/src/main/java/com/ptc/halo/controller/ProfessorAcademicController.java)
- [HALO/src/main/java/com/ptc/halo/controller/ProfessorDashboardController.java](HALO/src/main/java/com/ptc/halo/controller/ProfessorDashboardController.java)
- [HALO/src/main/java/com/ptc/halo/controller/ProfessorEngagementController.java](HALO/src/main/java/com/ptc/halo/controller/ProfessorEngagementController.java)
- [HALO/src/main/java/com/ptc/halo/controller/ProfessorProfileController.java](HALO/src/main/java/com/ptc/halo/controller/ProfessorProfileController.java)
- [HALO/src/main/java/com/ptc/halo/controller/ProfessorStudentController.java](HALO/src/main/java/com/ptc/halo/controller/ProfessorStudentController.java)
- [HALO/src/main/java/com/ptc/halo/controller/ProfilePhotoController.java](HALO/src/main/java/com/ptc/halo/controller/ProfilePhotoController.java)
- [HALO/src/main/java/com/ptc/halo/controller/QuizRevisionController.java](HALO/src/main/java/com/ptc/halo/controller/QuizRevisionController.java)
- [HALO/src/main/java/com/ptc/halo/controller/StudentAssessmentController.java](HALO/src/main/java/com/ptc/halo/controller/StudentAssessmentController.java)
- [HALO/src/main/java/com/ptc/halo/controller/StudentBadgeController.java](HALO/src/main/java/com/ptc/halo/controller/StudentBadgeController.java)
- [HALO/src/main/java/com/ptc/halo/controller/StudentDashboardController.java](HALO/src/main/java/com/ptc/halo/controller/StudentDashboardController.java)
- [HALO/src/main/java/com/ptc/halo/controller/StudentLearningProgressionController.java](HALO/src/main/java/com/ptc/halo/controller/StudentLearningProgressionController.java)
- [HALO/src/main/java/com/ptc/halo/controller/StudentProfileController.java](HALO/src/main/java/com/ptc/halo/controller/StudentProfileController.java)
- [HALO/src/main/java/com/ptc/halo/controller/StudentProgressController.java](HALO/src/main/java/com/ptc/halo/controller/StudentProgressController.java)
- [HALO/src/main/java/com/ptc/halo/controller/SuperAdminController.java](HALO/src/main/java/com/ptc/halo/controller/SuperAdminController.java)
- [HALO/src/main/java/com/ptc/halo/security/AuthenticatedAccount.java](HALO/src/main/java/com/ptc/halo/security/AuthenticatedAccount.java)
- [HALO/src/main/java/com/ptc/halo/security/CustomUserDetails.java](HALO/src/main/java/com/ptc/halo/security/CustomUserDetails.java)
- [HALO/src/main/java/com/ptc/halo/service/AdminActingSessionService.java](HALO/src/main/java/com/ptc/halo/service/AdminActingSessionService.java)
- [HALO/src/main/java/com/ptc/halo/service/AuthAuditService.java](HALO/src/main/java/com/ptc/halo/service/AuthAuditService.java)
- [HALO/src/main/java/com/ptc/halo/service/AuthService.java](HALO/src/main/java/com/ptc/halo/service/AuthService.java)
- [HALO/src/main/java/com/ptc/halo/service/PasswordResetService.java](HALO/src/main/java/com/ptc/halo/service/PasswordResetService.java)
- [HALO/src/main/java/com/ptc/halo/service/SelfProfileService.java](HALO/src/main/java/com/ptc/halo/service/SelfProfileService.java)
- [HALO/src/main/java/com/ptc/halo/service/StudentLessonAccessService.java](HALO/src/main/java/com/ptc/halo/service/StudentLessonAccessService.java)
- [HALO/src/test/java/com/ptc/halo/AdminActingSessionTest.java](HALO/src/test/java/com/ptc/halo/AdminActingSessionTest.java)
- [HALO/src/test/java/com/ptc/halo/AdminForcedPasswordTest.java](HALO/src/test/java/com/ptc/halo/AdminForcedPasswordTest.java)
- [HALO/src/test/java/com/ptc/halo/AdminProfessorModeTest.java](HALO/src/test/java/com/ptc/halo/AdminProfessorModeTest.java)
- [HALO/src/test/java/com/ptc/halo/AdminStudentModeTest.java](HALO/src/test/java/com/ptc/halo/AdminStudentModeTest.java)
- [HALO/src/test/java/com/ptc/halo/AdminStudentPreviewTest.java](HALO/src/test/java/com/ptc/halo/AdminStudentPreviewTest.java)
- [HALO/src/test/java/com/ptc/halo/AdminValidationHttpTest.java](HALO/src/test/java/com/ptc/halo/AdminValidationHttpTest.java)
- [HALO/src/test/java/com/ptc/halo/AssessmentAnswerSetHttpTest.java](HALO/src/test/java/com/ptc/halo/AssessmentAnswerSetHttpTest.java)
- [HALO/src/test/java/com/ptc/halo/AssessmentSubmissionAccessTest.java](HALO/src/test/java/com/ptc/halo/AssessmentSubmissionAccessTest.java)
- [HALO/src/test/java/com/ptc/halo/AuthenticatedIdentityRegressionTest.java](HALO/src/test/java/com/ptc/halo/AuthenticatedIdentityRegressionTest.java)
- [HALO/src/test/java/com/ptc/halo/AuthenticationAuditTest.java](HALO/src/test/java/com/ptc/halo/AuthenticationAuditTest.java)
- [HALO/src/test/java/com/ptc/halo/EngagementHttpTest.java](HALO/src/test/java/com/ptc/halo/EngagementHttpTest.java)
- [HALO/src/test/java/com/ptc/halo/LessonRevisionHttpTest.java](HALO/src/test/java/com/ptc/halo/LessonRevisionHttpTest.java)
- [HALO/src/test/java/com/ptc/halo/LoginIssuanceConcurrencyTest.java](HALO/src/test/java/com/ptc/halo/LoginIssuanceConcurrencyTest.java)
- [HALO/src/test/java/com/ptc/halo/LogoutRevocationTest.java](HALO/src/test/java/com/ptc/halo/LogoutRevocationTest.java)
- [HALO/src/test/java/com/ptc/halo/ModuleFileTransactionTest.java](HALO/src/test/java/com/ptc/halo/ModuleFileTransactionTest.java)
- [HALO/src/test/java/com/ptc/halo/PasswordChangeValidationHttpTest.java](HALO/src/test/java/com/ptc/halo/PasswordChangeValidationHttpTest.java)
- [HALO/src/test/java/com/ptc/halo/ProfessorAcademicHttpTest.java](HALO/src/test/java/com/ptc/halo/ProfessorAcademicHttpTest.java)
- [HALO/src/test/java/com/ptc/halo/ProfessorAccountConcurrencyTest.java](HALO/src/test/java/com/ptc/halo/ProfessorAccountConcurrencyTest.java)
- [HALO/src/test/java/com/ptc/halo/ProfessorDashboardScopeHttpTest.java](HALO/src/test/java/com/ptc/halo/ProfessorDashboardScopeHttpTest.java)
- [HALO/src/test/java/com/ptc/halo/ProfessorInputValidationTest.java](HALO/src/test/java/com/ptc/halo/ProfessorInputValidationTest.java)
- [HALO/src/test/java/com/ptc/halo/ProfessorModuleHttpTest.java](HALO/src/test/java/com/ptc/halo/ProfessorModuleHttpTest.java)
- [HALO/src/test/java/com/ptc/halo/ProfessorModuleOwnershipTest.java](HALO/src/test/java/com/ptc/halo/ProfessorModuleOwnershipTest.java)
- [HALO/src/test/java/com/ptc/halo/ProfessorStudentScopeHttpTest.java](HALO/src/test/java/com/ptc/halo/ProfessorStudentScopeHttpTest.java)
- [HALO/src/test/java/com/ptc/halo/QuizRevisionHttpTest.java](HALO/src/test/java/com/ptc/halo/QuizRevisionHttpTest.java)
- [HALO/src/test/java/com/ptc/halo/SelfProfileHttpTest.java](HALO/src/test/java/com/ptc/halo/SelfProfileHttpTest.java)
- [HALO/src/test/java/com/ptc/halo/SelfProfileTest.java](HALO/src/test/java/com/ptc/halo/SelfProfileTest.java)
- [HALO/src/test/java/com/ptc/halo/StudentAssessmentGenerationAccessTest.java](HALO/src/test/java/com/ptc/halo/StudentAssessmentGenerationAccessTest.java)
- [HALO/src/test/java/com/ptc/halo/StudentIncompleteProfileHttpTest.java](HALO/src/test/java/com/ptc/halo/StudentIncompleteProfileHttpTest.java)
- [HALO/src/test/java/com/ptc/halo/StudentPhotoHttpTest.java](HALO/src/test/java/com/ptc/halo/StudentPhotoHttpTest.java)
- [HALO/src/test/java/com/ptc/halo/SuperAdminActivityHttpTest.java](HALO/src/test/java/com/ptc/halo/SuperAdminActivityHttpTest.java)
- [HALO/src/test/java/com/ptc/halo/SuperAdminConcurrencyTest.java](HALO/src/test/java/com/ptc/halo/SuperAdminConcurrencyTest.java)
- [HALO/src/test/java/com/ptc/halo/SuperAdminSecurityTest.java](HALO/src/test/java/com/ptc/halo/SuperAdminSecurityTest.java)
- [HALO/src/test/java/com/ptc/halo/TestIdentity.java](HALO/src/test/java/com/ptc/halo/TestIdentity.java)

This report: HALO_AD3_IMPLEMENTATION_REPORT.md.
