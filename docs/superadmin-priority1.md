# Super Admin Priority 1 security changes

Scope: SA-01 through SA-05 only. No changes to AI behavior or Priority 2?4.

## IntelliJ setup
Open Run > Edit Configurations > the existing HALO Spring Boot run configuration > Modify options > Environment variables.
Add JWT_SECRET: a newly generated cryptographically random key of at least 32 bytes, Base64 encoded. No fallback exists. Do not use a memorable phrase or encode the old key. HS256 is explicitly used. A valid Base64 length check cannot measure randomness; generate the bytes with a secure generator/password manager. Do not put the key in frontend variables, source files, command-line arguments, or shared run-configuration XML.

For first installation ONLY (no SUPER_ADMIN row), also set SUPER_ADMIN_EMAIL and SUPER_ADMIN_PASSWORD (at least 12 characters; use a unique random temporary password). The email is trimmed/lowercased. Startup refuses incomplete bootstrap configuration or an email already assigned to another account. Existing Super Admins are never overwritten. Remove bootstrap environment variables once the account has been created and changed its temporary password.

A new bootstrap account is ACTIVE but must_change_password=true. Login returns mustChangePassword. Only authenticated GET /api/super-admin/profile and POST /api/auth/change-password are allowed until a password change/reset succeeds. Frontend redirects it to Profile. Backend enforces this even if browser state is altered. Password change clears the flag and revokes its token; sign in again.

## Rotation
1. Generate/store a new random JWT_SECRET securely.
2. Stop all old backend instances. Set the same replacement key in every intended instance and restart them. Do not leave an instance accepting the old key.
3. All old tokens become invalid; sign in again. Tokens without the version claim are also rejected.
4. Existing seeded Super Admin passwords are deliberately NOT changed on startup. Sign in and use Profile > Change Password, or the existing verified email-reset flow. Replace the old known password with a unique password. Changing environment bootstrap credentials does not change an existing account.
5. Update deployment secret storage, then remove retired bootstrap credentials. Do not publish either old or new secrets.

## Database change
UserEntity adds token_version BIGINT NOT NULL DEFAULT 0 and must_change_password BOOLEAN NOT NULL DEFAULT FALSE. Existing user passwords/roles remain unchanged. Current application uses Hibernate ddl-auto=update; it will add columns at startup. Back up the database and inspect the schema before deployment. For migration-managed deployments, add the equivalent two columns to user_entity once, before starting the new version (do not blindly rerun ALTER on existing columns):

ALTER TABLE user_entity ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE user_entity ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

Password change/reset obtains a pessimistic write lock, updates the encoded password, increments token_version, and clears must_change_password in one transaction. JWT validation compares the version against the current database account on every authenticated request. Old passwords/tokens are not used as revocation data. This shared-auth change necessarily applies to every role.

## Timeouts and logs
Shared Axios timeout: 15 seconds. Existing explicit endpoint timeouts remain respected. Multipart uploads and generation requests use 180 seconds when using the default. No automatic mutation retries. Dashboard/Profile offer manual page reload retries; Admins/Monitoring retain manual GET retry controls. Timeout text is: The request took too long. Please try again.
Super Admin raw Axios errors were removed. Shared development diagnostics contain bounded method/path/status/code only, without request bodies or headers. Password changes sign out after success because their previous JWT is revoked.

## Changed files
Backend (under HALO/src/main):
- java/com/ptc/halo/security/JwtService.java
- java/com/ptc/halo/security/JwtAuthenticationFilter.java
- java/com/ptc/halo/security/CustomUserDetails.java
- java/com/ptc/halo/entity/UserEntity.java
- java/com/ptc/halo/repository/UserRepository.java
- java/com/ptc/halo/component/SuperAdminInitializer.java
- java/com/ptc/halo/service/AuthService.java
- java/com/ptc/halo/service/PasswordResetService.java
- java/com/ptc/halo/dtoResponse/LoginResponse.java
- resources/application.properties
Frontend:
- src/services/apiClient.js
- src/utils/apiErrors.js
- src/utils/backendContract.js
- src/routes/ProtectedRoute.jsx
- src/pages/login/components/Login.jsx
- src/pages/superadmin/components/Admins.jsx
- src/pages/superadmin/components/SuperAdminDashboard.jsx
- src/pages/superadmin/components/SuperAdminProfile.jsx
- src/pages/admin/components/Monitoring.jsx (shared Super Admin timeout messages only)
- src/components/shared/ChangePasswordModal.jsx
Tests:
- HALO/src/test/java/com/ptc/halo/SuperAdminSecurityTest.java
- HALO/src/test/java/com/ptc/halo/StudentMentorHttpTest.java (update shared JWT validation test double)
- tests/frontend.test.mjs
- tests/api.test.mjs

## Verification limits
Tests generate ephemeral signing keys and use isolated repository doubles. Password-login verification uses the real BCrypt/DaoAuthenticationProvider. No real user passwords, production signing secrets, or live database records were changed. Full HaloApplicationTests context startup requires the real database/external configuration and is not part of the isolated run. The optional actual-upload test is skipped without its input-directory flag. Manual deployment checks remain: schema migration, environment configuration, real login/bootstrap, password reset email, old-token 401, and re-login.

Final checks: 50 backend tests selected, 49 passed, 1 optional real-upload test skipped; 7 new security tests passed. No backend test failures. The live-database HaloApplicationTests context test was not run. Java compilation succeeded as part of Maven test. Frontend: 28 tests passed, ESLint passed, Vite production build passed.
