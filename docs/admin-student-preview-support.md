# Admin Student Preview and Support Mode

## Behavior

The previous Student Mode used the real Student assessment and Mentor services: starting/submitting quizzes and opening/sending Mentor conversations persisted the selected Student's history. That capability remains available as **Support Mode**. The selector now recommends **Student Preview Mode** and requires an accessible confirmation dialog before creating a Support session.

- Preview UI: /admin/student-preview/:actingSessionId/*
- Support UI (unchanged route): /admin/student-mode/:actingSessionId/*
- Entry/selection: /admin/student-mode
- The authenticated principal, AuthContext, and JWT remain ADMIN.
- Professor Mode and ordinary Student/Professor route authorization are unchanged.

## Backend boundary

Preview uses /api/admin/preview/student/**. Support continues to use /api/admin/acting/student/**.

Preview session IDs are random UUID capabilities held only in a bounded process-local registry. They are **not** inserted into admin_acting_session. Therefore replacing a Preview URL with a Support URL, or changing a query/body parameter, cannot authorize a real Student write with a Preview ID. Support sessions likewise cannot be resolved by Preview endpoints.

Every Preview request checks the real Admin, session owner, expiry, target STUDENT role, active account, both token versions, and existence of the Student profile. Resource access uses the existing year/publication/progression rules. The target is taken from the server session only. No password/profile mutation or assessment generation endpoint is exposed.

Lifecycle:

| Method | Endpoint | Request |
| --- | --- | --- |
| POST | /api/admin/preview/student/sessions | { targetUserId } |
| GET | /api/admin/preview/student/sessions/{id} | Admin JWT |
| DELETE | /api/admin/preview/student/sessions/{id} | Admin JWT |

Workflow requests require X-Acting-Session. Read routes reuse existing dashboard, subjects, weeks, lesson, progress, badges, assessment questions, and profile services. Preview quiz start/submit/result/history and Mentor open/message/conversation/exchange routes match the corresponding Student service shapes, within the Preview namespace.

## Temporary quiz

The server captures the eligible published assessment's question set when starting a temporary attempt. It rechecks access and the question snapshot at grading. The shared pure answer-set validator rejects duplicate, missing, foreign, or invalid answers. Scoring and feedback are returned in memory with preview=true, correctCount, totalQuestions, percentage score, passed, and feedback. Correct answers are not included in the question-loading response.

Preview does not call real start/submit methods, save Student answers, update module completion/progress, or award badges. Repeating a completed preview submit returns its existing temporary result. Preview practice does not unlock later weeks; navigation continues to reflect the selected Student's real progression.

## Temporary Mentor

Mentor's existing module capture, material index, retrieval, relevance checks, prompts, citation formatting, and state revalidation are reused. Indexing/AI processing occurs outside DB transactions. Access/session/token/material changes during external work reject the result. Only the temporary registry receives messages; no Mentor session or message row is created.

Request UUIDs retain idempotent exchanges for the lifetime of the temporary chat. Concurrent work returns PREVIEW_OPERATION_PENDING (409); retrying the same key after completion returns the existing exchange. Conversation windows contain the latest 30 messages with a stable before-ID cursor. Client reconciliation retrieves the exchange rather than automatically repeating the POST.

The existing material-index cache may be prepared/reused. This is a module cache, not Student academic history.

## State, audit, and deployment

Preview expires after 30 minutes, ends on explicit exit/change-account, and is lost on server restart. Limits: 100 concurrent previews per application process, 10 temporary quizzes and 5 chats per preview, 20 exchanges per chat. PREVIEW_LIMIT_REACHED asks the Admin to exit and start another preview. Expired entries are removed on registry access/creation.

This implementation requires a single backend instance or sticky routing for Preview requests. A request landing on another process safely rejects an unknown Preview session; it cannot fall back to real acting. A distributed ephemeral store would be a separate deployment enhancement.

Start/end preview audit events record the real Admin plus selected Student and role using existing nullable provenance. They intentionally have no persisted acting-session FK. They contain neither answers nor Mentor bodies. Support mutation audits retain real Admin, target Student, and database-backed acting session. No migration or preview table is required.

Frontend API adapters are instance-scoped, with separate namespaces and cancellation scopes. Mode + session identity keys remount Student pages on switching, clearing temporary UI state. Exit/revocation aborts requests; late results are ignored. Real account data remains read-only in Preview. Both acting profiles continue to hide credential controls.

## Verification

Automated tests cover no-write preview grading/chat; existing-history preservation; session isolation and role/status/token checks; real/preview capability separation; shared citations, short transactions, stale rejection and idempotency; Support confirmation; API scoping; target switching; and quiz/Mentor lost-response reconciliation.

AI responses and material-index results are mocked in automated preview tests. No live Gemini or PDF verification is claimed. Browser visual inspection of the new selector/banners/dialog still requires a running authenticated environment.

## Changed files

Backend production code (relative to HALO/src/main/java/com/ptc/halo/):
- controller/AdminStudentPreviewController.java (new)
- controller/AdminStudentModeExceptionHandler.java
- dtoResponse/PreviewAssessmentResult.java (new)
- service/AdminStudentPreviewService.java (new)
- service/AdminActingSessionService.java
- service/ActivityLogService.java
- service/AssessmentAnswerSet.java (new pure shared validator)
- service/AssessmentService.java
- service/MentorService.java

Backend tests (HALO/src/test/java/com/ptc/halo/):
- AdminStudentPreviewTest.java (17 database-backed tests)
- AdminStudentPreviewHttpTest.java (6 HTTP/security tests)

Frontend:
- src/services/admin/studentModeService.js
- src/pages/admin/acting/StudentMode.jsx
- src/pages/admin/acting/StudentMode.module.css
- src/pages/student/components/StudentPageShell.jsx
- src/pages/student/components/Quiz.jsx
- src/pages/student/components/LessonChat.jsx
- src/routes/AppRoutes.jsx
- src/utils/lessonErrors.js
- tests/admin-student-mode.test.mjs
- tests/fixtures/backend-contract.json
- docs/admin-student-preview-support.md

## Final automated results

- Backend compile: passed.
- All backend test classes executed: 318 tests total, 317 passed, 1 optional existing uploaded-PDF test skipped (halo.test.uploads not configured).
- Application context startup passed in a separate H2 run with test-only JWT/bootstrap/provider properties. The other 317 tests ran without a JWT override so the existing missing-secret security test remained meaningful: 316 passed, 1 skipped.
- Frontend: 156 tests passed, zero failures.
- ESLint: passed.
- Vite production build: passed.

Logs: HALO/student-preview-backend-tests.log, HALO/student-preview-context-test.log, student-preview-frontend-tests.log, student-preview-lint.log, student-preview-build.log.
