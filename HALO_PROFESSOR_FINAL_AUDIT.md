# HALO Professor Final Code Review and Functionality Audit

Date: October 19, 2026 (Asia/Manila)

**Final verdict: NEEDS FIXES FIRST.**

The normal dashboard, current Progress view, ownership checks, revision isolation, and quiz-version preservation have substantial passing coverage. However, reachable legacy Student APIs expose unrelated Student information, published lesson revisions do not retain immutable content history, and an interaction between quiz approval and lesson revisions can strand a draft. These should be resolved before treating the Professor role as ready for final acceptance or panel demonstration.

## Audit scope and evidence

This was review only. No source code, tests, migrations, configuration, or live database records were changed during this audit. The working tree already contained earlier Admin/registration changes; those were left untouched. This report and test/build output artifacts are the audit deliverables.

Evidence is distinguished as follows:

- **Source-confirmed:** frontend handlers, backend controllers/services/repositories, entity mappings, and security configuration traced together. A source-confirmed edge case does not mean it was reproduced against a live account.
- **Automated:** existing backend regression tests, React/JSDOM frontend suite, lint, and production build. No new regression source was added because this is a review-only task.
- **Live database:** MariaDB 10.4.32 on localhost:3307, using explicit READ ONLY transactions. The existing opt-in Professor dashboard test also enforces read-only connections, disables DDL, and compares the dashboard roster against SQL and Progress.
- **Live HTTP:** unauthenticated GET /api/professor/dashboard on localhost:8081 returned 401. Authenticated Professor endpoint checks were automated MockMvc tests, not live logged-in HTTP acceptance tests.
- **Manual browser:** not performed. CSS/DOM coverage is not visual browser certification. No live login, upload, generation, publication, lifecycle change, or assessment submission was performed.

## Confirmed findings

### F1 — High: legacy Professor Student endpoints bypass academic eligibility

**Affected files:**
- `HALO/src/main/java/com/ptc/halo/service/ProfessorStudentService.java:45` (`getStudents`)
- Same file, `getStudentProgress`, `getStudentBadges`, and `requireStudent` at line 312
- `HALO/src/main/java/com/ptc/halo/controller/ProfessorStudentController.java`
- `HALO/src/main/java/com/ptc/halo/controller/AdminProfessorPreviewController.java:51`
- `HALO/src/main/java/com/ptc/halo/controller/AdminProfessorModeController.java:79`

The default paginated Progress API correctly scopes the roster, but GET `/api/professor/students` still calls `findByRole(STUDENT)` and returns every Student's name, email, and academic profile metadata, regardless of subject eligibility or lifecycle state. Detail routes such as `/{userId}/progress` and `/{userId}/badges` validate only that the supplied ID belongs to a Student. An unrelated Professor can therefore request that Student's identity/profile details and institution-wide badge information. Completed-module and assessment aggregates themselves are restricted to the Professor's ownership; that restriction does not protect the separately returned personal information.

These routes are reachable behind the PROFESSOR role guard. The Admin Professor Preview/Acting adapters reuse the same broad methods. This is not merely unused helper code. Existing tests even expect the broad list and zero-count detail response for an unrelated Professor, so a green test suite does not establish the desired privacy boundary.

**Recommendation:** apply the same academic eligibility and explicit current/former population rules to every Professor Student entry point; return a safe 404/403 for unrelated targets. Preserve intentionally labeled institution-wide achievements only for Students the Professor may actually view. Add negative tests for an authenticated Professor with no eligible subjects and for direct ID requests.

### F2 — High: historical lesson revision content is overwritten

**Affected files:**
- `HALO/src/main/java/com/ptc/halo/entity/LessonRevisionEntity.java:6`
- `HALO/src/main/java/com/ptc/halo/service/LessonRevisionService.java:66`

`lesson_revisions` uses moduleId as its sole primary key. `start()` retrieves the existing row, increments revisionNumber, changes its state to DRAFT, and overwrites its payload. `publish()` updates the live module in place; the preceding live lesson snapshot is not archived elsewhere by this flow. Starting another revision overwrites the prior published revision payload and author metadata. Discard clears the payload to `{}`. Audit events remain, but event text is not a recoverable history of lesson content/material sets.

**Impact:** current Student learning records remain intact, but the requested historical revision content is not preserved. The database currently has one revision row (module 37, revision 1, PUBLISHED); that snapshot does not demonstrate retention across multiple revisions.

**Recommendation:** design immutable published revision snapshots with a separate draft/current pointer, retaining material references and author/publication metadata. Determine migration requirements before implementation; do not attempt to reconstruct missing history by inventing content.

### F3 — High: approving a quiz can strand an existing lesson draft revision

**Affected files:**
- `HALO/src/main/java/com/ptc/halo/service/LessonRevisionService.java:54`
- Same file, `start`, `save`, `discard`, and `prepare`
- `HALO/src/main/java/com/ptc/halo/service/QuizRevisionService.java:66`
- `HALO/src/main/java/com/ptc/halo/entity/AiLearningModuleEntity.java:18`

Lesson draft validity requires `revision.baseVersion == module.version`. Quiz approval clears assessmentNeedsReview and flushes the same versioned module entity. When that flag changes from true to false, the module version advances even though lesson materials did not change.

Source-confirmed sequence:
1. A published lesson revision has flagged its quiz for review.
2. A replacement quiz draft is generated against module version V.
3. The Professor starts the next lesson revision, also based on V.
4. The Professor approves the quiz; the module becomes V+1.
5. Save, Review, Publish, and even Discard of the lesson draft now fail with STALE_MODULE_OPERATION.
6. Reload returns the same draft, and Start returns the existing draft without rebasing it.

This is a workflow dead end, not evidence of lost Student attempts. No such stale draft exists in the current live snapshot, and this sequence was not executed against live data.

**Recommendation:** separate material-publication identity from unrelated module metadata changes, or introduce an explicit safe recovery/rebase path. Allow safe discard of a stale draft without weakening publication concurrency checks. Add an isolated regression for this exact interleaving.

### F4 — Medium: initial quiz publication has weaker validation than regenerated quizzes

**Affected files:**
- `HALO/src/main/java/com/ptc/halo/service/AssessmentService.java:97`
- `HALO/src/main/java/com/ptc/halo/service/AssessmentAiService.java`
- `HALO/src/main/java/com/ptc/halo/service/ProfessorModuleService.java:approveLesson`
- `HALO/src/main/java/com/ptc/halo/service/AdminProfessorModeService.java:approve`
- Contrast: `HALO/src/main/java/com/ptc/halo/service/QuizRevisionService.java:validate`

Initial lesson approval generates a quiz and immediately persists it as AVAILABLE with approvedAt. It checks only that the generated question collection is nonempty. Unlike the revision path, it does not enforce exactly ten questions, unique/ranged question numbers, unique question text, nonblank options, or an A/B/C/D answer key. JSON parsing and prompt instructions do not enforce these requirements. For example, a one-character `Z` answer key satisfies the entity's length constraint but cannot be selected by a Student.

Also, the first quiz becomes available as part of lesson approval, without a separate Professor review of those newly generated questions. Replacement quizzes correctly remain DRAFT until explicitly approved. Confirm whether this difference is intentional before changing the approval policy.

The live snapshot contains no invalid answer keys, non-ten-question quizzes, or duplicate current quiz versions. This is a confirmed missing validation path, not a claim that current stored quizzes are corrupt.

**Recommendation:** share server-side generated-quiz validation between initial and replacement generation; add malformed-provider-response tests. Explicitly agree on whether first publication must also follow Generate -> Review -> Approve.

### F5 — Low: failed initial Assessment panel load has no local retry

**Affected file:** `src/pages/professor/components/QuizRevision.jsx:14`

If getQuizRevision fails, the component displays an error but has no Reload/Retry action. All useful controls are inside `state && ...`; with no initial state, the Professor must refresh or leave/re-enter the page. This contrasts with LessonRevision's explicit Reload revision action.

**Recommendation:** provide a safe read-only retry with loading/error reset; preserve any current review/draft state when appropriate.

No Critical issue was confirmed during this audit. No cross-Professor content-write bypass was found in the inspected ownership-protected subject/module/revision paths. F1 is a confirmed read/privacy boundary problem.

## Features working correctly within the verified paths

| Area | Verified behavior and limits |
| --- | --- |
| Dashboard roster | Counts distinct ACTIVE Student users with an existing year-level match to at least one owned subject. EXISTS avoids double-counting across subjects. Matches current Progress population. |
| Dashboard teaching counts | Subjects/modules/approved modules are owned by the Professor. Assessments counts current AVAILABLE/INACTIVE assessments, excluding retired/draft versions. Passed Attempts counts submitted, valid scored, passing attempts from eligible ACTIVE Students across versions. |
| Performance | Bounded 28-day daily average of submitted scores in academic scope; each attempt contributes once. Inactive/archived/blocked users excluded. Friendly empty state and accessible data table accompany the chart. |
| Needs Attention | Counts modules with review flags/draft quizzes, DRAFT/REVIEWED lesson revisions, or completed generation awaiting approval; links carry actual subject/week IDs. |
| Recent Teaching Activity | Bounded to six real MODULE/ASSESSMENT logs for the Professor or Admin acting on their behalf, with readable formatting. |
| Subject/week ownership | Owned-ID repository lookups precede reads/writes. Other Professor content yields safe not-found responses. Role guards and backend input validation remain in place. Week-number uniqueness is enforced. |
| Subject deletion/history | Nonempty subject deletion is rejected; foreign-key protection and transactions remain. No audit/history deletion was performed. |
| Material upload | Signature/content validation for PDF/PNG/JPEG, 3 MB per file, ten-file limit, bounded image dimensions, safe storage-root resolution, and transactional file cleanup. |
| PDF/index handling | Readable pages survive mixed blank pages; all-textless files still fail. Cache fingerprints include module/file identity and source bytes. Draft indexing uses a separate non-live build path. |
| Relevance/generation | Extracted content is checked semantically against the module context before generation. Separate unreadable/relevance errors, generated-section checks, and stale-result protection are covered by tests. Actual provider behavior remains unverified. |
| Published/draft separation | Normal published materials reject direct edits. Draft payloads do not replace live module content. Review/publish use short persistence phases, revalidation, fingerprints, and post-commit live-index invalidation. This works for a revision cycle; F2/F3 concern history and cross-workflow edge cases. |
| Replacement quiz workflow | Validated ten-question draft; Professor approval retires the previous current quiz and activates the replacement. Current quiz remains available before approval. |
| Student attempt preservation | Attempts retain direct assessment-version references; an unfinished attempt resumes its original version. New starts without unfinished attempts use the current version. Existing answers/scores are not recalculated on quiz replacement. Status reads have transaction coverage. |
| Current/former Progress | CURRENT = ACTIVE; FORMER = INACTIVE + ARCHIVED; BLOCKED excluded from both. Zero-activity eligible Students remain visible. Module activity and engagement are read-only and scoped. |
| Progress summaries | Student total uses the complete paged population; completed modules/passed attempts are explicitly labeled “this page.” Institution-wide achievements are explicitly labeled. Legacy detail APIs remain affected by F1. |
| Learning engagement | Bounded, paginated aggregates use lesson study days, assessment start/submission, completion, and nonempty Student Mentor messages. Distinct days/sessions prevent per-message inflation. Preview temporary activity is not persisted. |
| Profile | Shared authenticated self-name/photo editing, validation, preview/save/cancel, and shared identity state. Professor ID/email/role are not editable through the self-name endpoint. Acting target name/photo are read-only. |
| Authentication/settings | Role guards, token-version invalidation, password-related checks, logout confirmation/retry, saved per-account theme, Light auth pages, and Admin theme inheritance have regression coverage. |
| Professor Preview/Acting | Separate preview credentials, backend write rejection, ownership/session/expiry checks, and actor-vs-target audit identity. Preview hides revision/quiz mutation panels. No live acting session was created. |

## Read-only live MariaDB results

Snapshot during this audit; other users may continue using the system. These values are not a promise that the database remains frozen.

| Professor user ID | My Students | My Subjects | Total Modules | Approved Modules | Assessments (current) | Passed Attempts |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 9 | 0 | 0 | 0 | 0 | 0 | 0 |
| 10 | 0 | 0 | 0 | 0 | 0 | 0 |
| 12 | 0 | 0 | 0 | 0 | 0 | 0 |
| 13 | 0 | 0 | 0 | 0 | 0 | 0 |
| 16 | 0 | 0 | 0 | 0 | 0 | 0 |
| 21 | 10 | 7 | 11 | 11 | 11 | 1 |
| 35 | 8 | 1 | 1 | 1 | 1 | 1 |
| 36 | 0 | 0 | 0 | 0 | 0 | 0 |

Data sources: user_entity + student_profile_entity + subjects/professor_entity for academic eligibility; weeks/ai_learning_modules for module counts; assessments filtered to current statuses; assessment_attempts with ACTIVE-user, ownership, year-level, submittedAt, valid-score, and passed predicates.

Additional checks:
- All eight ACTIVE Professors have required Professor metadata.
- There are 13 ACTIVE and 2 ARCHIVED Student accounts. Three ACTIVE accounts lack Student profiles; academic year-level eligibility cannot safely be inferred for them, so they are not included in Professor-specific rosters.
- Professor 21: current roster 10; former roster 2 archived. Professor 35: current roster 8; former roster 1 archived. The same Student may legitimately be eligible for different Professors; counts should not be summed institution-wide.
- No current INACTIVE/BLOCKED Students existed for a live lifecycle-state comparison. Exclusion/restoration is covered with isolated test fixtures, not live mutations.
- Professor 21 performance: Oct 10 = two attempts averaging 20%; Oct 12 = one at 100%; Oct 18 = one at 40%. Professor 35: Oct 18 = one at 100%.
- Professor 21 Needs Attention: one assessment needing review (module 37 has draft quiz V5), zero unpublished lesson revisions, zero modules awaiting approval. Professor 35: all zero.
- Module 37: V1 retired with completed attempt 17 (40%) and unfinished attempt 18 for Student 18; V4 is AVAILABLE; V5 is DRAFT. V2/V3 remain retired. This is expected version preservation, not evidence that a new attempt selects an old quiz.
- Professor 21's latest activity includes real V5 draft generation and V4/V3/V2 approvals/generation; the chart/activity sections are not mock data.
- No stale DRAFT/REVIEWED lesson revisions currently exist; F3 is a source-confirmed interleaving risk.
- Snapshot totals: 8 assessment attempts, 3 progress rows, 100 Mentor messages, 516 activity rows, 1 lesson revision row. No audit operation changed them. Counts can change independently through other users.
- MariaDB reported tx_read_only=1 and session timezone SYSTEM. The host is Singapore Standard Time (UTC+08:00), matching Manila's current offset. Professor calendar code uses JVM/server-local dates with Monday weeks, not an explicitly pinned Asia/Manila zone. Keep deployment timezone aligned and verify near-midnight behavior on the deployment host.

## UI/UX and responsiveness

Source inspection and the existing DOM tests support the following:
- Dashboard six-card grid is three columns on desktop, two at tablet widths, one on narrow mobile; the performance/attention layout collapses separately.
- Profile, table overflow regions, focus states, dialogs, and acting shell reuse shared HALO components.
- LessonEditor main layout uses `gap: var(--space-6)` (24px). Assessment, Published Version, Lesson Materials, and AI-Generated Lesson surfaces share theme tokens and consistent surfaces/padding.
- Revision/assessment confirmations use the shared Dialog body portal, fixed backdrop, centered flex layout, z-index 100, viewport-limited scrolling, padding, rounded surface, focus trap, accessible labels, and busy dismissal protection.
- Buttons wrap/stack on smaller screens; long source names wrap. Light/Dark colors are token-based.
- Failed revision/quiz mutations retain confirmation/error state. Initial quiz-panel load recovery is F5.

Not certified visually: actual desktop/mobile viewport rendering, browser zoom, keyboard/screen-reader operation, chart tooltip placement, and pixel-level light/dark contrast. These remain manual acceptance checks. Professor Preview intentionally omits quiz/revision editor panels; confirm the desired read-only preview coverage for those panels. Week listing has no explicit server ORDER BY or client sort in the inspected path; verify ordering when weeks are created out of sequence.

## Test execution

Backend command selection:
`Professor*Test,LessonRevision*Test,QuizRevision*Test,Assessment*Test,Module*Test,AiGenerationServiceTest,LessonMaterialRelevanceTest,AdminProfessor*Test,SelfProfile*Test,StudentPhoto*Test,LogoutRevocationTest,AuthLogoutHttpTest`
with `-Dhalo.test.dashboard.live=true`.

- **Backend:** 237 discovered/executed by Surefire: **236 passed, 0 failed, 0 errors, 1 skipped**; compile/test build succeeded.
- **Live dashboard test:** passed for all eight ACTIVE Professors against MariaDB, comparing roster counts with independent SQL and Progress, and checking bounded dashboard sections.
- **Skipped:** `ModuleScopedMentorTest.realUploadedTextPdfIndexesWithoutProvider` requires `halo.test.uploads`. It was not enabled because it builds a cache under the supplied upload directory. No live uploaded-PDF/provider verification is claimed.
- **Frontend:** full serial Node suite, **219 passed, 0 failed, 0 skipped**.
- **Lint:** passed.
- **Production build:** passed.
- Logs: `professor-audit-backend.log`, `professor-audit-frontend.log`, `professor-audit-lint.log`, `professor-audit-build.log`.

The passing suite does not cover every finding: some legacy-scope tests encode the broad behavior identified in F1; multi-revision historical snapshots and the F3 interleaving are not established by the existing tests. Full application context startup against writable live MariaDB was not run, to avoid bootstrap/schema side effects.

## Incomplete or unverified workflows

- Live Gemini relevance decisions, scan transcription, lesson quality, malformed provider output, rate limits/timeouts, and regeneration recovery.
- Actual browser Professor login-to-publication, initial quiz review policy, simultaneous lesson/quiz edits, and all mobile/dark-mode visuals.
- Live password-reset email/OTP delivery and mutating profile/photo/password workflows.
- Authenticated live HTTP using a real Professor/acting session; no session was created or token replay attempted against live accounts.
- Multi-instance/restart behavior: preview credentials are process-local and expire; no clustered deployment certification.
- High-volume roster/query performance and long-running initial approval: ordinary Professor initial approval includes indexing/quiz generation inside its transactional flow, unlike the shorter revision/acting phases. Load/timeouts need measured testing; no live contention failure was reproduced here.
- Durable historical lesson revisions are not merely unverified: F2 confirms the storage model cannot retain the requested immutable content history.

## Prioritized recommendations and readiness

1. Fix F1 before exposing the Professor role to final acceptance testers; audit every Student-related Professor/acting endpoint with out-of-scope authenticated users.
2. Implement durable revision history and resolve the stale-draft interleaving (F2/F3), with isolated regression coverage and an explicit migration plan if needed.
3. Share initial/replacement quiz validation and agree on first-quiz approval policy (F4). Preserve all historical versions, answers, attempts, and scores.
4. Add local assessment-load retry (F5); verify week ordering and read-only Preview coverage.
5. Run browser acceptance and controlled live AI/SMTP tests with approved test accounts after the fixes. Keep live historical records intact.

**Final verdict: NEEDS FIXES FIRST.** Automated checks are green, but confirmed privacy and revision-integrity/workflow gaps prevent an unconditional final-testing or panel-demonstration sign-off. No fixes were applied during this audit.
