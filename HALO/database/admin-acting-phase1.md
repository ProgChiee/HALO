# Admin acting sessions — Phase 1

This phase only creates, validates, and revokes acting sessions. It grants no Professor or Student workflow access. Existing JWT identity and normal role routes are unchanged.

## Deployment

Apply admin-acting-phase1.sql manually after backup and metadata review, with application writers stopped. It has NOT been applied by this implementation. MySQL DDL auto-commits: inspect partial schema after any failure rather than rerunning blindly. No existing records are rewritten.

The project's current ddl-auto=update can automatically modify schema on application startup. For controlled deployment set SPRING_JPA_HIBERNATE_DDL_AUTO=validate, apply the reviewed migration first, then start the updated application. Do not start this version against an unmigrated production database. Tests use isolated H2 schema creation, not the deployed database.

## API

All endpoints require the real ADMIN JWT and ADMIN authority; the service reloads the actor and checks current ADMIN role, ACTIVE status and completed mandatory password change.

- GET /api/admin/acting/targets?role=PROFESSOR&page=0&size=20&search= — active targets, role PROFESSOR or STUDENT only; literal case-insensitive name/email search; deterministic user-ID order. Size capped at 100. Returns content, page, size, totalElements, totalPages. Target fields: userId, name, email, role.
- POST /api/admin/acting/sessions — body {"targetUserId":123,"targetRole":"PROFESSOR"}; returns 201 with id, adminUserId, target, createdAt, expiresAt.
- GET /api/admin/acting/sessions/{id} — returns the validated stored session (200). No target override is accepted by the service.
- DELETE /api/admin/acting/sessions/{id} — revokes an owned session (204). Repeated revocation remains 204 and does not duplicate the audit. Expired or target-invalid sessions can still be revoked by their active Admin owner.

Session IDs are random UUIDs, not authentication credentials. They never replace the Admin JWT. Sessions last 30 minutes from creation, without sliding renewal. Actor/target IDs and role snapshots cannot be updated. Validation rejects expiry, revocation, changed actor/target token versions, target role changes, or inactive targets. A changed/inactive actor is denied. Another Admin receives the same 404 as a missing session.

Errors retain {status, code, message}: validation 400 VALIDATION_FAILED; unsupported/mismatched target role 400 INVALID_TARGET_ROLE; missing target/session or foreign session 404 RESOURCE_NOT_FOUND; inactive target on creation 409 ACTING_TARGET_INACTIVE; expired/revoked/invalidated session 409 ACTING_SESSION_INVALID. Genuine authentication/authorization remain 401/403; unexpected failures are sanitized 500 ADMIN_REQUEST_FAILED.

## Audit

ActivityLog.user remains the real Admin. Nullable actingTarget, actingTargetRole, actingSession record provenance. Session creation and first revocation each write an ACCOUNT audit in the same transaction. Audit failure rolls back the session mutation. Revocation locks the session row to avoid duplicate revoke events. Existing createLog behavior is unchanged and leaves all acting fields null. No passwords, tokens, or request payloads are audited.

No automatic session cleanup deletes audit history. Later workflow phases must resolve this server-side context and revalidate before committing long-running work; the session alone does not enable those operations today.
