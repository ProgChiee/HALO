# Frontend alignment with HALO.zip

Reference: the supplied `HALO.zip`, Java controllers, DTOs, enums, SecurityConfig,
and relevant service implementations. The lesson editor additionally follows the
updated local AiLearningModuleController.java supplied by the user (2026-09-29).
This editor update changes frontend files only. Earlier upload-limit work changed
backend application.properties to 3MB per file and 32MB per request; the ZIP was unchanged.

## Access and unavailable features

| Feature | Controller contract | Frontend behavior |
| --- | --- | --- |
| Admin monitoring | GET `/api/admin/activity-logs`, `/professors/monitoring`, `/students/monitoring`; ADMIN only | Three tabs use these responses. |
| Superadmin monitoring | GET `/api/super-admin/activity-logs`, `/admins/monitoring`; SUPER_ADMIN only | Dedicated `/superadmin/monitoring` route; activity log is admin activity as filtered by the controller. |
| Professor/student account management | `/api/admin/professors`, `/api/admin/students`; ADMIN only | Removed these links and route permissions for superadmins. |
| Subject/week CRUD | `/api/professor/subjects`, `/api/professor/weeks`; PROFESSOR only | Professor page retained. Old admin subject URL displays an explanatory page; removed admin CRUD calls/menu entry. AdminService contains academic methods, but AdminController does not expose them. |
| Login sessions, AI usage, quiz-performance aggregates | No `/admin/login-activity`, `/admin/ai-usage`, or `/admin/quiz-performance` mappings | Unsupported calls removed. Actual professor/student monitoring data is used. No fabricated session metrics. |
| Profile updates | Profile controllers expose GET only | Removed unused PATCH wrappers. Password changes remain supported. |
| Mentor session creation | POST `/api/student/mentor/open/{moduleId}` | Removed nonexistent `/mentor/start` wrapper. |

## Payload and response types

- `VITE_API_URL` includes `/api`. If omitted, requests use same-origin `/api`.
- Java `Long`/`Integer` values are JSON numbers, `Boolean` values are booleans,
  enums are their exact uppercase strings, and LocalDateTime values are date-time
  strings without a timezone. The UI does not invent a UTC offset.
- Login returns a flat `{ name, email, token, role }`. Only known backend roles
  map to frontend roles; a usable token is required. Stored user/token pairs are
  restored from the same storage, not mixed between localStorage and sessionStorage.
- Registration sends `{ name, email, password, studentId, section, yearLevel }`.
  Valid year levels are `FIRST_YEAR` and `SECOND_YEAR`.
- Password reset sends `{ email, otp, newPassword }`; password change sends
  `{ currentPassword, newPassword }`.
- Account status PATCH requests have no body. The returned record is used to
  update the list and open detail view. `BLOCKED` is displayed distinctly.
- Activity log filtering sends `type`, not `activityType`, as the query name.
- Professor monitoring uses `userId`, `name`, `moduleActivities`, `lastActivity`.
  Student monitoring uses `userId`, `name`, `latestAssessmentScore`,
  `completedModules`, `passedAssessments`, `totalBadges`. Charts show the top 25;
  summary cards still use all returned records.
- Superadmin admin monitoring uses `adminId` and `accountActivities`.
- Subject creation/update uses `subjectCode`, `subjectName`, `description`,
  `yearLevel`. A new week uses a positive integer `weekNumber` and `title`.
- Module creation is multipart: `weekId`, optional `lessonText`, `youtubeLink`,
  `aiNotes`, and repeated `files`. Approve/decline use PUT; generate uses POST.
- Mentor messages use the session ID and `{ message }`. Conversation senders are
  `STUDENT` and `HALO`; replies contain `haloMessage`.
- Assessment submission uses an attempt ID with
  `{ answers: [{ questionId, answer }] }`, where answer is `A`, `B`, `C`, or `D`.
  The submit controller fills only `attemptId`, `score`, and `passed` even though
  the DTO also declares feedback. The UI fetches GET `/student/assessment/result/{attemptId}`
  for detailed review and separately refreshes eligibility.
- Starting an assessment reuses an unfinished attempt, as implemented in
  AssessmentService. There is no draft-answer save/restore endpoint; the UI
  explains that answers are saved on submission. Passing updates module progress
  in the backend. Progress percentage uses completed weeks divided by total weeks.

## Frontend fixes

- StrictMode-safe chat mount handling, request locks, and fresh component state
  when lesson/quiz/editor route parameters change.
- Shared locking for generate/approve/decline and serialized account toggles.
- Cancellable resource and week loading; each subject tracks its own loading/error
  state. Post-creation week refresh invalidates any older pending request.
- Next-lesson links require `unlocked && lessonAvailable && !completed` and search
  other incomplete subjects when needed. Unpublished week buttons are disabled.
- At most five per-student progress requests run concurrently. Individual failures
  show unavailable values rather than silently claiming zero progress.
- Fixed chart tooltips, retry notifications, missing CSS classes, and stale speech
  callbacks. Toast context values are stable. Pages are lazy loaded.
- Public auth requests omit bearer tokens. A 401 only clears the matching current
  session, so stale failures do not clear a newer login. Auth form errors no longer
  dump Axios request credentials to the console.

## Verification and remaining integration requirements

`npm run lint`, `npm test`, and `npm run build` validate the frontend locally.
Tests use a controller/DTO snapshot extracted into
`tests/fixtures/backend-contract.json`, plus mocked Axios adapters. They do not
send requests to the real backend or prove database/AI/email behavior.

The updated professor controller exposes GET
`/api/professor/ai-learning-modules/week/{weekId}`. The editor restores the module,
materials and saved files for every status. Only 204 means no module; failed loads
block saving and offer retry. Existing drafts use PUT for text and POST
`/{moduleId}/files` with a singular `file` field for each added file.
Successful uploads are removed from the pending queue individually. Saved files
can be deleted; material changes reset generation and require regeneration.
The editor counts saved and pending files together (maximum 10, 3MB each).

Publishing flow: Save Materials, Generate Lesson with AI, review, then Approve &
Publish. Only APPROVED lessons are published. Pending changes block generation
and approval; published lessons are read-only, matching the backend restriction.
Restart the backend with the updated controller before using this editor.
Live database, AI generation and student publication still need an authenticated
integration check.

Session metrics and admin academic management require actual controller
support if those features are intended.

SecurityConfig allows localhost/127.0.0.1 browser origins only. A deployed frontend
on another origin requires a backend CORS update. Bearer-token storage remains the
backend's existing JWT design; client-side role checks do not replace server authorization.

## Student LessonChat update (2026-10-02)

The mentor flow now uses shared year-level subject eligibility and APPROVED/COMPLETED
checks across lesson reads and mentor operations. POST opens a session without
a new Gemini request; generated lesson sections are displayed directly.
See [full changes, error contract and verification](lessonchat-fix/README.md) and
[complete source files](lessonchat-fix/FULL-CODE.md). Backend files were applied to
`C:/Users/User/Desktop/HALO`; restart IntelliJ to load them.

## Actual workspace generation review (2026-10-02)

The actual backend has moved to `C:/Users/User/Desktop/halo-frontend/halo-frontend/HALO`.
The frontend is `C:/Users/User/Desktop/halo-frontend/halo-frontend/src`; no separate
frontend directory was found. IntelliJ should run this HALO project, with its
working directory set to HALO so relative uploaded-file paths resolve correctly.
The earlier ZIP and docs/lessonchat-fix are historical snapshots, not the source
of truth for the current generation service.

Inspected generation service, status enums, module entity/repository/controller,
request/response DTOs, student lesson/mentor controllers and services, progression,
student profile eligibility, JWT filter and SecurityConfig. Student authorization
files already match the previously applied fix, so they were not duplicated or
replaced. Endpoints, DTO fields, entities and repository methods are unchanged.

Files changed in this review:
- HALO/src/main/java/com/ptc/halo/service/AiGenerationService.java: sets lesson status
  PENDING instead of null; rejects regeneration of APPROVED lessons; requires a
  boolean valid field and all four nonempty textual sections; technical/file/API/
  JSON failures save FAILED and return handled HTTP 502; raw AI replies are not logged.
  Existing professor-guidance fallback is preserved. Repository saves remain outside
  an encompassing service transaction so throwing the HTTP error does not roll back
  the saved FAILED state under the current controller flow.
- src/pages/professor/components/LessonEditor.jsx: reloads persisted module state
  after generation errors, removing stale generated previews; blocks actions when
  this refresh fails until the user retries loading.
- HALO/src/test/java/com/ptc/halo/AiGenerationServiceTest.java: 11 new regression
  tests for success, technical failures, validation, fallback and manual rejection.
- docs/backend-alignment.md: records current paths, changes and verification.

Status compatibility: AiGenerationStatus already contains PENDING, COMPLETED,
DECLINED and FAILED. DECLINED is retained for explicit semantic rejection only
(no usable educational content), preserving existing data/API compatibility.
Technical failures use FAILED. Manual professor rejection changes only
LessonStatus to DECLINED; successful generation remains COMPLETED. Generation
alone never publishes a lesson. No database migration or enum removal is required.

Verification: Maven offline test-compile succeeded (Java 17), including all imports
and new tests. Existing deprecation warnings remain. The 11 generation checks plus
17 student access/mentor checks passed using the cached JUnit/Mockito and Spring
TestContext runner (28 total), as the normal Maven Surefire dependencies were not
fully available in the prior run. Frontend lint, 17 tests and production build passed.
No real Gemini call or live database/student login was performed.

Restart and test in IntelliJ:
1. Open this workspace's HALO/pom.xml, use Java 17 and HALO as working directory.
2. Stop any old backend instance on 8081; rebuild and run HaloApplication.
3. Refresh the frontend. Generate a draft: expect COMPLETED and Pending Review.
4. In development, trigger an invalid/missing uploaded file or provider failure:
   expect a handled 502, persisted FAILED, cleared preview and Retry Generation.
5. Restore valid inputs, regenerate and approve; an eligible active student should
   read all four sections and open the mentor using POST with module.id.
6. Declining a generated draft should leave AI COMPLETED and set lesson DECLINED.

Subject eligibility remains based on matching student-profile and subject year
level; this codebase has no separate per-subject enrollment table. Spring Security
and the existing previous-week progression checks remain enforced.
