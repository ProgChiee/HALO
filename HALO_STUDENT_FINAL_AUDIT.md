# HALO Student Final Code Review and Functionality Audit

**Audit date:** 22 Oct 2026 (Asia/Manila)  
**Scope:** Student role and its required Professor/Admin integration points.  
**Verdict:** **NEEDS FIXES FIRST** for an unqualified panel demonstration. Core access, assessment, engagement, and security flows are well covered and automated checks pass, but one confirmed account-data issue makes three active Student accounts unable to use profile and subject flows, and two dashboard/history presentation gaps remain.

This was a read-only review. No application source, configuration, migration, or live database record was changed. The only new artifact is this audit report. MariaDB checks used read-only transactions. The latest available live snapshot was taken on 22 Oct 2026 at approximately 11:50 Asia/Manila.

## 1. Features working correctly

### Dashboard and learning activity

- Dashboard counts come from persisted module progress, passed assessment attempts, Student badges, and the latest submitted attempt. The latest score query orders submitted attempts by timestamp and excludes unfinished attempts.
- Weekly study days and the calendar deduplicate dates across lesson study, assessment start/submission, module completion, and non-empty Student Mentor messages. The week starts Monday; future dates are excluded.
- Study streak counts contiguous active dates and continues only when the most recent active date is today or yesterday. Live Student 18 data at the audit snapshot had activity on 18 and 19 Oct, and no newer activity: **1/7 active days this week, 0-day streak** on 22 Oct. Student 28 had activity on 19 Oct: **1/7, 0-day streak**. These match the stated date rule.
- Mentor frequency counts distinct sessions with at least one non-empty Student message, rather than counting messages. Live counts for Student 18 were 0 sessions this week and 7 this month; Student 28 had 1 this week and 1 this month.
- Continue Learning asks the backend for eligible subjects and sorted week access, then chooses the first published, unlocked, incomplete lesson. Completed or unavailable work is not selected. Dashboard sections expose loading and retry states.
- The Study Calendar supports month navigation, marks today separately, labels active dates for assistive technology, and caps returned activity at the selected month and today.

### Subjects, lessons, and access

- Student subject eligibility is based on the Student profile’s year level.
- Direct lesson/week/assessment routes validate the authenticated Student, active account status, year-level eligibility, published/generated lesson state, and prior-week completion. A URL or guessed module ID alone does not grant access.
- Lesson study recording repeats those access checks and is excluded from Preview Mode. Daily uniqueness and a per-Student lifecycle lock prevent repeated refreshes from inflating study days.
- Published lesson materials and revisions use separate draft state; Student lesson and Mentor reads use published content. Revision publication invalidates and rebuilds the module index. Draft material is not used as the live Student source.
- Video Learning validates and extracts the YouTube ID before constructing the embed URL; the iframe is removed when its dialog closes.

### AI Mentor

- Mentor sessions are tied to a Student and module. Session reads and writes check ownership and module access. Conversation windows are bounded, and duplicate message submissions use request IDs.
- Substantive answers retrieve module-specific material and validate selected source IDs internally. Greetings and acknowledgements can use a natural-response path without citations. The source citation text is intentionally not displayed to Students.
- Admin Student Preview uses temporary in-memory quiz/chat state and separate Preview APIs; it does not write real Student attempts or Mentor messages. Support Mode uses real Student operations under an acting session and audit context.
- Error handlers return safe status/error codes and avoid sending exception details or upstream payloads to the browser.

### Assessments, progress, and badges

- Student assessment endpoints are role-protected. Starts and submissions check lesson access and serialize lifecycle writes by locking the Student row.
- Answers are validated as a complete set of unique question IDs and A/B/C/D choices. The server scores against stored correct answers; Students cannot submit scores or edit quiz questions through Student endpoints.
- Duplicate submissions return a conflict. A Student can resume an unfinished attempt, and that attempt remains attached to its original assessment version. New attempts use the current available version. Result reads check attempt ownership.
- Progress and badge writes occur transactionally with assessment submission. Badge type is unique per Student in the schema and awards recheck under the Student lifecycle lock.
- The live database showed module 37 with quiz Version 4 AVAILABLE and Version 5 DRAFT. Student 18’s unfinished attempt 18 remained tied to Version 1 RETIRED, while the previously submitted Version 1 attempt 17 retained its 40% score. This is consistent with preserving the in-progress version and historical result.
- Live score verification recomputed all six submitted attempts from stored answer rows; each matched its stored score. There were no answers joined to a question from a different assessment and no duplicate Student/badge-type pairs in the checked snapshot.

### Profile, settings, authentication, and integration

- Full-name changes use the authenticated identity and accept only the self-profile payload. Role, email, account status, and academic fields are not part of the update.
- Profile photo routes use the authenticated owner ID; acting-mode photo reads are target-scoped and read-only. Password changes and logout preserve the existing security flow.
- JWTs include a token version checked on each authenticated request. Logout increments the stored version, so replaying the old token is rejected.
- Student paths require the Student role; request filters also reject inactive accounts. Cross-Student Mentor and assessment result access are checked by ownership.
- Existing Preview and Support behaviors are covered in backend/frontend regression tests. Preview retains the Admin identity and inherits the Admin theme; Support actions remain real and audited.

## 2. Confirmed findings

### S1 — High: active Students without profiles cannot use core Student pages

**Evidence:** Read-only MariaDB showed 13 ACTIVE Students and 2 ARCHIVED Students. Three active user rows—IDs 26, 31, and 32—have no student_profile_entity row. The missing profile is the root cause.

StudentSubjectService.getStudentSubjects throws when the profile lookup is empty, and ProfileService.getStudentProfile does the same. Consequently, those accounts cannot load enrolled subjects or their Student Profile. Subject/week access also requires a profile, and Admin Preview creation checks for one, so these incomplete accounts cannot enter the normal academic or Preview flow. The dashboard’s user-ID-based counts can still return, which makes the partial failure harder to understand.

**Affected files:** HALO/src/main/java/com/ptc/halo/service/StudentSubjectService.java, ProfileService.java, StudentLessonAccessService.java, AdminStudentPreviewService.java.

The current registration service validates and creates the user/profile in one transaction, so this audit did not find evidence that the current registration method intentionally creates profileless Students. The live rows’ creation history is not sufficient to establish whether they came from an older flow, manual/imported data, or an earlier failed process. No live records were repaired.

### S2 — Medium: no submitted assessment shows as “0% latest score”

The API correctly returns null when a Student has no submitted attempt, but the Dashboard renders summary.latestAssessmentScore ?? 0 with a percent sign. A Student with no score therefore sees **0%**, which is indistinguishable from a real zero score.

**Affected file:** src/pages/student/components/StudentDashboard.jsx (latest-score card).  
**Root cause:** frontend fallback presentation, not the backend score query.

### S3 — Medium: previous quiz results are not discoverable after leaving the result screen

The backend stores attempt history and exposes GET /api/student/assessment/attempts/{moduleId} and GET /api/student/assessment/result/{attemptId}. The frontend quiz service wraps the history endpoint, but the Student Quiz component does not use it or restore a submitted result on reload. The detailed result is kept in component state after submission; after navigation or refresh, the Student cannot reopen a prior attempt’s result from the Student UI.

This does not delete or alter the historical score. It is a Student-facing history/review gap.

**Affected files:** src/pages/student/components/Quiz.jsx, src/services/student/quizService.js.

### S4 — Low: Profile and Progress failures require a manual page refresh

Dashboard, calendar, lesson, and quiz paths have explicit retry controls. The Profile and Progress error states only tell the Student to refresh; neither offers an in-page retry action. This is a recoverability inconsistency rather than a data or authorization defect.

**Affected files:** src/pages/student/components/Profile.jsx, src/pages/student/components/Progress.jsx.

No confirmed Critical security issue was found in the reviewed Student routes. The profileless-account finding is High for the affected users, but is not evidence of cross-account access.

## 3. Live database snapshot

MariaDB reported 10.4.32, @@system_time_zone=Asia/Singapore, and @@tx_read_only=1 in the read-only check. The running Java service reported Asia/Manila. These zones shared UTC+08:00 at the audit time, and SQL/JVM active-day results agreed. No database writes were issued.

| Student user ID | Completed modules | Passed attempts | Badges | Latest submitted score | Week active days (Mon 19–Sun 25 Oct) | Streak on 22 Oct | Mentor sessions this week / month |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 18 | 2 | 2 | 7 | 100% | 1 | 0 | 0 / 7 |
| 28 | 0 | 0 | 0 | No submitted score | 1 | 0 | 1 / 1 |
| 44 | 0 | 0 | 0 | No submitted score | 0 | 0 | 0 / 0 |

Student 18’s activity dates were 10–15, 18, and 19 Oct. The absence of activity on 20–22 Oct means the streak is 0 on the 22nd, while the current week still has one active day (the 19th). This is expected under the streak rule.

Assessment review found 8 attempts in total: 6 submitted and 2 unfinished. All six submitted scores matched recalculation from their 10 stored answers. For module 37, Version 4 was the current available quiz and Version 5 remained a draft; the old Version 1 attempts were preserved.

The snapshot is point-in-time data and can change after the audit.

## 4. UI/UX and accessibility observations

- Dashboard uses compact responsive summary/activity grids, a contained calendar, readable subject progress indicators, and retry states for the main data panels. Continue Learning safely wraps long lesson titles.
- Quiz flow blocks starting when the server says it is unavailable/already passed, shows the quiz version, prevents empty answers, and reports submission state. The current feedback shows after submission but is not persisted into a reopenable Student history page (S3).
- Profile photo interaction uses a circular clickable avatar, preview/save/cancel controls, and a hidden file input. Full-name editing is shared across roles.
- Student modules use shared HALO theme variables and responsive breakpoints. Accessibility attributes exist on the calendar, quiz choice buttons, and Mentor input/dialogs.
- Automated DOM/CSS checks passed. No manual browser inspection at desktop/tablet/mobile sizes was performed, so visual clipping, focus behavior in the running browser, screen-reader experience, and live video playback remain unverified.

## 5. Incomplete or unverified workflows

- No authenticated live Student browser session or live authenticated HTTP request was performed. Unauthenticated GET checks to Dashboard, Subjects, Assessment Status, Mentor Session, Progress, and Badges returned 401 on the running backend.
- Live Gemini/provider quality, prompt behavior under production credentials, provider timeout/rate limits, real uploaded PDF extraction/transcription, and citations against the production material index require separate controlled live-AI verification.
- Email/OTP delivery, mutating photo/password/profile actions, and real Student Preview/Support writes were not exercised against live accounts.
- Browser visual acceptance for dark mode, keyboard-only navigation, mobile layout, and YouTube playback is not represented by automated tests.
- High-volume data/query performance was not load-tested.

## 6. Verification results

- **Backend focused regression suite:** 207 tests, 0 failures, 0 errors, 2 skipped; Maven BUILD SUCCESS.
- **Live MariaDB dashboard test:** StudentDashboardMariaDbLiveTest passed against the configured MariaDB using a read-only datasource/transaction. It compared dashboard metrics with stored records for all Students.
- **Skipped tests:** realUploadedTextPdfIndexesWithoutProvider in ConversationalMentorTest and ModuleScopedMentorTest. They require halo.test.uploads; this was not enabled because indexing can write a .module-index cache beside uploaded files.
- **Frontend full test suite:** 219 passed, 0 failed, 0 skipped.
- **Frontend lint:** passed.
- **Frontend production build:** passed.
- **Live HTTP:** unauthenticated Student API checks returned 401. No authenticated live HTTP/session checks were made.
- **Manual browser review:** not performed.
- **Repository state:** no application source, configuration, migration, or live data changes made for this audit.

## 7. Prioritized recommendations and verdict

1. Repair the account-creation/import path that produced the three profileless active users, then handle missing profiles with explicit safe errors or a supported administrative recovery path. Do not fabricate Student IDs or year levels.
2. Render “No score yet” (or an em dash) when latest score is null; reserve 0% for an actual submitted zero score.
3. Add a Student-facing attempt-history/result entry point that uses the existing ownership-checked APIs, or clearly define that detailed result feedback is available only immediately after submission.
4. Add an in-page retry action to Profile and Progress loading failures.
5. Before panel sign-off, perform authenticated browser acceptance with test accounts, including one attempt on an older in-progress quiz, a newly approved quiz version, Preview/Support read-only/write boundaries, and mobile/dark-mode layouts. Use a controlled live AI test for real uploaded materials.

**Final verdict: NEEDS FIXES FIRST.** Authentication, data isolation, access checks, quiz version preservation, score calculations, badges, and engagement aggregation have strong passing coverage, and the live calculations matched the database snapshot. The three active profileless accounts and the confirmed score/history UI gaps should be resolved or explicitly accepted before final Student-role demonstration.
