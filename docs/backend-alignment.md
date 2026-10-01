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
