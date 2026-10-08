# Admin Professor Mode backend — Phase 2

No new migration. Requires the applied Phase 1 acting-session/audit schema. No frontend or Student Mode workflows are included.

## Request authorization

Use the real ADMIN Bearer token plus the X-Acting-Session header containing the UUID returned by Phase 1 creation. Every endpoint validates the session owner, role PROFESSOR, expiration, revocation, target ACTIVE state/token version, actor role/status/token version, and Professor profile existence. Client professor IDs cannot replace the session target. The normal /api/professor routes remain PROFESSOR-only. No principal replacement or JWT role change occurs.

All routes below have prefix /api/admin/acting/professor. Methods, fields and response shapes follow the existing Professor contracts:

| Method | Suffix | Behavior |
|---|---|---|
| GET | /dashboard | Target-scoped teaching statistics |
| GET | /profile | Target profile read only |
| GET, POST | /subjects | List/create owned Subjects |
| GET, PUT, DELETE | /subjects/{id} | Owned Subject read/update/delete |
| GET, POST | /subjects/{id}/weeks | List/create owned Weeks |
| GET, PUT, DELETE | /weeks/{id} | Owned Week read/update/delete |
| GET | /ai-learning-modules/week/{id} | Module for owned Week; 204 when absent |
| POST | /ai-learning-modules | Multipart weekId, lessonText, youtubeLink, aiNotes, files |
| GET, PUT | /ai-learning-modules/{id} | Read/update owned module |
| POST | /ai-learning-modules/{id}/generate | Generate/regenerate eligible draft |
| PUT | /ai-learning-modules/{id}/approve | Check materials, generate assessment, publish |
| PUT | /ai-learning-modules/{id}/decline | Decline eligible generated lesson |
| POST | /ai-learning-modules/{id}/files | Multipart file upload |
| DELETE | /ai-learning-modules/files/{id} | Delete owned file |
| GET | /students | Existing global student-account list policy |
| GET | /students/progress-summaries | Existing page/size contract; target-scoped learning metrics |
| GET | /students/{id}/progress | Target Professor's scoped Student progress |
| GET | /students/{id}/subjects | Target Professor's scoped Student subjects |
| GET | /students/{id}/assessments | Target Professor's scoped assessment history |
| GET | /students/{id}/badges | Existing institution-wide achievement policy |

Subject/Week/module DTO validation and safe Professor error handling are reused. Missing or malformed session header is 400. Invalid/expired/revoked session is 409 ACTING_SESSION_INVALID; Student-mode session is 400 INVALID_TARGET_ROLE; foreign/missing session or academic resource is 404. Genuine authentication/authorization stay 401/403. No credential/password/profile mutation is exposed.

## Transactions, audit and external work

Short transactions lock the acting-session row against concurrent revocation. Shared academic/module services enforce the existing Subject -> Week -> Module -> File ownership queries using the stored target. Normal Professor controller behavior delegates to the same module service and normal audit calls remain self-attributed.

Every acting academic mutation records ActivityLog.user = Admin with acting target/role/session provenance and the original activity category. Audit failure rolls back that mutation, including existing file rollback/delete coordination.

Generation has an audited short start phase, external preparation/AI without a DB transaction, and a short final phase that revalidates acting access and resource ownership before the existing generation token/version check. Final result and audit commit together. Revocation, expiry, changed ownership, or changed module state reject final writes. The already committed, audited PENDING start may remain after a rejected/failed final phase; no stale generated result is saved and there is no automatic retry.

Approval captures an owned, generated module with original files in a short transaction. Material indexing and assessment AI run outside it. Final persistence revalidates acting access, ownership, approval requirements and captured module version, then atomically saves APPROVED status, assessment/questions and audit. Failure leaves the module unpublished and no assessment/questions from that operation.

Prompts/model configuration, normal Professor role rules, Student rules, upload validation/storage, and assessment scoring rules are unchanged. External AI/provider side effects cannot be undone when access changes, but their results are not persisted after invalidation.

## Verification

Tests use H2 plus controlled AI/index collaborators, covering transaction boundaries, session revocation/expiry, stale versions, ownership, audit rollback and existing self-service regressions. A live Gemini/provider call is not part of these tests.
