# HALO subject covers

## Behavior

All Professor Subject Management, Student My Subjects, and Browse Subjects cards use the shared SubjectCover component. Student subject details and Curriculum Preview use the same rendering. The existing supplied asset is served at /image/halo-subject-default.png, unchanged. It retains its white background and uses object-fit: contain; custom images use object-fit: cover. The image area reserves a responsive 16:9 ratio with rounded upper corners.

Subject creation remains unchanged and requires no image. After creation, an authorized Professor can select Change cover on a subject card, choose an optional JPEG/PNG/WebP, inspect its local preview, and Save cover. Existing custom covers offer Replace image and Remove custom cover. Cancel does not upload. Removal restores the watermark. Saving uses a duplicate-request lock and loading/error feedback; successful changes reload that card's cover. Existing academic controls and subject IDs remain intact.

## Storage and API

No schema change or migration is required. The new private storage follows the existing profile-photo pattern: one normalized PNG named from the immutable subject ID, under halo.subject-cover-directory (default uploads/subject-covers). Uploaded filenames never become paths. No default images or large binaries are inserted into the database.

SubjectResponse and StudentSubjectResponse add a coverUrl resource field, e.g. subjects/7/cover. This is an authenticated resource relative to the current role/mode API, not a public filesystem URL and not a claim that a custom file exists. GET returns PNG bytes or 204 when there is no custom image. Clients without the field or with null/missing/failed image data show the local default. Existing clients may ignore the additive field.

Endpoints:
- Normal Professor: GET, PUT (multipart image), DELETE /api/professor/subjects/{id}/cover.
- Normal Student: GET /api/student/subjects/{id}/cover.
- Existing Professor acting mode: scoped GET/PUT/DELETE /api/admin/acting/professor/subjects/{id}/cover.
- Professor Preview: scoped GET /api/admin/preview/professor/subjects/{id}/cover only.
- Student Support/Preview: scoped GET /api/admin/{acting|preview}/student/subjects/{id}/cover only.

Images are fetched through the existing authenticated workflow adapters as blobs, with cancellation and object-URL cleanup. No JWT is placed in an image URL, and arbitrary URLs returned by an API are not loaded. Responses use no-store and nosniff. Professor mutations are ownership-scoped; Student reads reuse the current My Subjects/Browse visibility contracts, including manageable unavailable active enrollments. A cover does not authorize lesson access. Acting changes retain original-actor audit attribution. Preview and Student Support do not gain image-write permissions.

## Validation and file lifecycle

- **3 MB upload limit**, matching the existing server-wide multipart limit. The global setting and lesson/PDF upload behavior were not changed.
- Actual image decoding and format checks; JPEG, PNG and WebP accepted regardless of the original filename.
- Maximum 8192 pixels per dimension and 16 megapixels before decoding; output is proportionally resized to at most 1600 pixels per side.
- Output is freshly encoded PNG, stripping input metadata and non-raster payloads.
- WebP decoding uses the pinned [TwelveMonkeys imageio-webp 3.15.3](https://central.sonatype.com/artifact/com.twelvemonkeys.imageio/imageio-webp/3.15.3) dependency.
- Replacement is staged in the same directory and installed with an atomic move only after the ownership/audit transaction commits. Failed validation or transaction rollback leaves the existing cover intact. Temporary files are cleaned on normal completion/rollback.
- Successful subject deletion emits a transactional cleanup event; rollback preserves both subject and cover. Cleanup failures are logged without undoing completed academic transactions.
- Audit records describe a requested replacement/removal. Database and filesystem are not one distributed transaction: a filesystem failure after audit commit can leave that request log with the old cover intact and an error response. The UI does not falsely claim success.

## Files changed

Backend:
- HALO/pom.xml
- HALO/src/main/java/com/ptc/halo/controller/SubjectCoverController.java (new)
- HALO/src/main/java/com/ptc/halo/service/SubjectCoverService.java (new)
- HALO/src/main/java/com/ptc/halo/service/SubjectCoverStorage.java (new)
- HALO/src/main/java/com/ptc/halo/service/ProfessorAcademicService.java (post-commit deletion cleanup event)
- HALO/src/main/java/com/ptc/halo/repository/SubjectRepository.java (owned cover mutation lock)
- HALO/src/main/java/com/ptc/halo/dtoResponse/SubjectResponse.java
- HALO/src/main/java/com/ptc/halo/dtoResponse/StudentSubjectResponse.java
- HALO/src/test/java/com/ptc/halo/SubjectCoverTest.java (new)
- HALO/src/test/java/com/ptc/halo/SubjectCoverHttpTest.java (new)

Frontend:
- src/components/shared/SubjectCover.jsx (new)
- src/components/shared/SubjectCover.module.css (new)
- src/pages/professor/components/SubjectCoverDialog.jsx (new)
- src/pages/professor/styles/SubjectCoverDialog.module.css (new)
- src/pages/professor/components/SubjectManagement.jsx
- src/pages/professor/styles/SubjectManagement.module.css
- src/pages/student/components/EnrollmentSubjects.jsx
- src/pages/student/components/Subjects.jsx
- src/pages/student/components/CurriculumPreview.jsx
- src/services/professor/professorService.js
- src/services/student/studentService.js
- tests/subject-cover.test.mjs (new)
- tests/fixtures/backend-contract.json (new routes added)

This report is new. The supplied public/image/halo-subject-default.png was used, not regenerated or edited. Prior working-tree changes were preserved.

## Verification

- Focused backend: **41 passed**, zero failures/errors/skips, including 9 storage/service tests and 4 HTTP tests.
- Focused frontend/contract: **21 passed**, zero failures/skips, including 8 cover regressions.
- Complete frontend: **391 passed**, zero failures/skips.
- Complete backend: **825 total, 814 passed, 0 failures, 0 errors, 11 intentional skips**. The skips are 9 opt-in live MariaDB tests and 2 existing Mentor tests. Executed in the isolated test copy with synthetic H2 fixtures, not live halo_db.
- Backend compilation: passed.
- Frontend lint: passed, no warnings/errors.
- Production build: passed.

Coverage includes no-cover creation/existing subjects, PNG/JPEG/WebP decoding, upload/replace/remove, malformed/oversized/unsupported inputs, dimensions, ownership, audit rollback, deletion rollback/cleanup, existing membership/Browse visibility, normal and mode-specific routes, read-only restrictions, fallback, target-change cancellation, preview-before-save, duplicate submission, Cancel, focus containment/restoration, scroll lock, outside-click and Escape protection, CSS aspect ratio/theme compatibility, and unchanged enrollment/Professor regressions.

## Deployment and limitations

- Configure a private persistent upload volume via halo.subject-cover-directory; include it in backups alongside the database. Do not use an ephemeral container filesystem. Multiple backend instances need the same persistent storage and an atomic-move-capable filesystem.
- Deploy the backend and frontend together and include the supplied public asset. No live migration is needed. Existing subjects automatically show the default without a backfill.
- A process crash can leave staged .tmp files; normal transaction cleanup is covered, crash recovery is not automated. Review old temporary files through an approved maintenance process. Permanent-deletion cleanup failures are logged for follow-up.
- Student image reads deliberately reuse existing list visibility logic; large catalogs may benefit from a dedicated equivalent visibility query later. No eligibility policy was duplicated or changed here.
- No connected browser visual/E2E or native MariaDB upload/concurrency checks were performed. Responsive/light-dark checks are component and CSS assertions, not browser screenshots. Backend writes used isolated H2 and temporary filesystem fixtures only.
- No live halo_db records/schema, production connection settings, migrations, academic rules, commits, or pushes were changed.
