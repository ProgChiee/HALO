# Private learning materials: implementation and verification

## Outcome

Implemented local/private-Supabase storage selection, authenticated attachment bytes, safe Student file metadata, PDF/image preview and download, and an offline-first migration tool. No live Aiven, MariaDB, or Supabase operations, migrations, deployment, commits, or pushes were performed. No schema change is required.

## Behavior and security

- FileUploadService, ModuleMaterialIndex, and AiGenerationService read/write originals through LessonMaterialStorage. Local development remains the default; the Supabase adapter stores private originals in halo-learning-materials. Module index caches remain local and rebuildable.
- Existing actual PDF/PNG/JPEG parsing, 3 MB limits, upload endpoints, UUID names, and transaction cleanup remain in place. Storage never returns a public or signed URL to the frontend.
- Student byte reads validate the actual attachment's module through existing active-enrollment, academic-profile, publication, and progression checks, then recheck after retrieval. Professor reads enforce subject ownership. Preview/Support use existing validated target-session wrappers. No new role permissions or write bypasses were introduced.
- Student responses expose only attachment id, sanitized original filename, and media type. React fetches blobs through the authenticated API client; preview URLs are revoked, downloads reauthenticate, duplicate requests are suppressed, and shared Dialog accessibility is reused. Professor UI design is unchanged.
- Original paths and immutable revision payloads are preserved. The retention guard checks current file rows, draft payloads, and history before after-commit deletion; malformed retention data retains bytes. Rollback cleans up newly uploaded objects. A crash or ambiguous remote failure can leave an orphan; automatic destructive garbage collection is deliberately absent.
- Configurable CORS, existing PORT/VITE_API_URL support, and a filesystem-first Vercel SPA fallback support deployment. Production credentials remain backend-only.

## Executed verification

| Verification | Result |
|---|---|
| Complete isolated backend suite | 849 total: 838 passed, 0 failures, 0 errors, 11 skipped |
| Final focused backend rerun (after final null-type guard and three additional regression cases) | 105 total: 104 passed, 0 failures, 0 errors, 1 skipped |
| Complete frontend suite | 411 passed, 0 failed, 0 skipped |
| Focused frontend/API/migration suite | 32 passed, 0 failed |
| Frontend lint | Passed, no errors/warnings |
| Vite production build | Passed |
| Backend compilation and Spring Boot JAR packaging | Passed, tests intentionally skipped for packaging; executed separately above |
| Git whitespace check | Passed |
| Vercel configuration | JSON and filesystem-first fallback checked locally; no hosted verification |

The complete backend run preceded the final three added checks; the final focused run includes them. Totals overlap and must not be added. The final focused tests cover storage provider selection without network startup, private-bucket enforcement, path traversal, redirect rejection, size bounds, rollback cleanup, historical retention, authenticated ownership, active enrollment/progression, post-fetch revalidation, every mode byte-route delegation, CORS, and existing upload/revision/enrollment regressions.

Existing complete-suite skips: AdminMonitoringMariaDbLiveTest (6), ProfessorDashboardMariaDbLiveTest (1), ProfessorStudentScopeMariaDbLiveTest (1), StudentDashboardMariaDbLiveTest (1), ConversationalMentorTest (1), ModuleScopedMentorTest (1). No skips were added or live-test opt-ins enabled. Backend verification used an isolated H2 test copy with synthetic settings and local mock HTTP storage; it is not native Aiven/MariaDB or real Supabase verification. Frontend tests use DOM/mocked APIs, not connected browser E2E. Existing deprecation/unchecked compilation warnings remain; frontend tests also printed Vite test-server port contention messages but completed successfully.

Initial runs found two old filesystem-mock fixtures incompatible with the storage interface, one lint regex error, and a protected-constructor mistake in a newly added test. Those were corrected and the affected checks rerun successfully. Original business/security assertions were preserved.

## Deployment and migration

See [deployment, environment, backup, migration and rollback instructions](private-learning-materials.md). Required production settings: HALO_LESSON_STORAGE_PROVIDER=supabase, SUPABASE_URL, SUPABASE_SECRET_KEY, HALO_CORS_ALLOWED_ORIGINS, frontend VITE_API_URL, plus HALO_LESSON_STORAGE_ROOT for cache and HALO_LESSON_LEGACY_ROOTS for any historical absolute references. Existing database/JWT/AI configuration remains required and unchanged.

Dry run is offline. Apply requires an explicit bucket-confirmation flag, verifies remote SHA-256 against the backup, resumes by rechecking remote objects, refuses overwrite/deletion, and does not connect to a database. A complete inventory must include live files, drafts, and immutable history. No actual uploads backup migration was run. The operator must independently verify inventory completeness and recover any missing historical bytes before cutover.

## Readiness and limitations

Ready for isolated staging validation, not production sign-off. Outstanding: real private Supabase bucket/API behavior and permissions; approved checksum-verified migration; Aiven compatibility/concurrency; Render/Vercel startup/routing/CORS; browser PDF/image preview and download across devices; live AI provider/index rebuild; historical path inventory completeness. Other local uploads (subject covers/profile images) are outside this learning-material change and still need deployment review. Reference scans are conservative and may need future performance work as history grows. Already downloaded bytes cannot be revoked. Post-cutover rollback must preserve new remote uploads before returning to a local store.

## Exact changed files

- `HALO/src/main/java/com/ptc/halo/component/ModuleMaterialIndex.java`
- `HALO/src/main/java/com/ptc/halo/config/LessonStorageConfiguration.java`
- `HALO/src/main/java/com/ptc/halo/config/SecurityConfig.java`
- `HALO/src/main/java/com/ptc/halo/controller/LessonAttachmentController.java`
- `HALO/src/main/java/com/ptc/halo/dtoResponse/AiLearningFileResponse.java`
- `HALO/src/main/java/com/ptc/halo/service/AiGenerationService.java`
- `HALO/src/main/java/com/ptc/halo/service/FileUploadService.java`
- `HALO/src/main/java/com/ptc/halo/service/LessonAttachmentService.java`
- `HALO/src/main/java/com/ptc/halo/service/LessonMaterialRetention.java`
- `HALO/src/main/java/com/ptc/halo/service/LessonMaterialStorage.java`
- `HALO/src/main/java/com/ptc/halo/service/LessonObjectKeys.java`
- `HALO/src/main/java/com/ptc/halo/service/LessonStoragePaths.java`
- `HALO/src/main/java/com/ptc/halo/service/StudentLessonService.java`
- `HALO/src/main/java/com/ptc/halo/service/SupabaseLessonMaterialStorage.java`
- `HALO/src/main/resources/application.properties`
- `HALO/src/test/java/com/ptc/halo/LessonAttachmentAccessTest.java`
- `HALO/src/test/java/com/ptc/halo/LessonAttachmentHttpTest.java`
- `HALO/src/test/java/com/ptc/halo/LessonMaterialRelevanceTest.java`
- `HALO/src/test/java/com/ptc/halo/PrivateMaterialStorageTest.java`
- `HALO/src/test/java/com/ptc/halo/UploadStreamLifecycleTest.java`
- `docs/private-learning-materials-verification.md`
- `docs/private-learning-materials.md`
- `scripts/migrate-learning-materials.mjs`
- `src/pages/student/components/LessonAttachments.jsx`
- `src/pages/student/components/LessonChat.jsx`
- `src/pages/student/styles/LessonAttachments.module.css`
- `src/services/student/studentService.js`
- `tests/fixtures/backend-contract.json`
- `tests/lesson-attachments.test.mjs`
- `tests/material-migration.test.mjs`
- `vercel.json`
