# Professor learning engagement

## Deployment

Apply `learning-engagement-study-days.sql` manually before deploying this feature.
It has **not** been applied by this change. Stop writers, select the intended HALO
database, inspect for an existing table, and use `ddl-auto=validate` for controlled
deployment. The migration adds only `lesson_study_days`, with Student/module foreign
keys, a unique Student/module/date key, and a module/date lookup index. It does not
rewrite existing data or invent historical lesson opens.

## Scope and definitions

HALO currently assigns subjects by year level, without enrollment tables. Both the
Professor Progress roster and engagement roster include all STUDENT accounts whose
profile year matches a subject owned by that Professor, even with zero activity.
No activity in another Professor's modules contributes to the counts.

Active Study Days is the distinct union of calendar dates from lesson-study rows,
assessment starts/submissions, completed-module timestamps, and non-empty STUDENT
Mentor messages. Repeated opens, multiple modules, and mixed activities on a day
count once per Student. Previously recorded quiz/Mentor/completion dates remain
usable; lesson-only history starts at deployment.

AI Mentor Sessions counts distinct real session IDs containing a non-empty STUDENT
message **during the reporting period**. Multiple messages count once. A reused
session can count in each period in which it is used. Welcome-only/empty sessions
do not count. There are no temporary Preview records in the queried tables.

Periods run from Monday or the first of the month through today, using the same
server-local calendar as HALO's existing `LocalDateTime.now()` timestamps. The API
returns the server timezone. Keep deployment timezone stable; there is no separate
Student timezone preference in the current model.

## APIs and persistence

- `GET /api/professor/students/engagement?page=0&size=20`
- `GET /api/admin/acting/professor/students/engagement?page=0&size=20`

Both return `content`, `number`, `size`, `totalElements`, `totalPages`, `first`,
`last`, `timezone`, and `weekStartsOn`. Each row has `userId`, `name`,
`activeDaysWeek`, `activeDaysMonth`, `mentorSessionsWeek`, `mentorSessionsMonth`.
Default size is 20, maximum 100. Three queries per populated page: roster count,
bounded roster, and grouped aggregates over that page and date window. No history
rows are materialized in application memory and there are no per-Student queries.

- `POST /api/student/ai-learning-modules/week/{weekId}/study`
- `POST /api/admin/acting/student/ai-learning-modules/week/{weekId}/study`

The lesson UI calls this only once the lesson is actually displayed, not during
dashboard lookups. Authorization/publication/progression validation runs before
recording. The existing Student row lock serializes inserts; database uniqueness
also protects the daily key. The Support endpoint validates its acting session
and records Admin audit provenance in the same transaction. There is no Preview
study endpoint, and Preview UI never sends this request. Read-only lesson endpoints
remain read-only. Tracking failure does not block studying and uses sanitized
diagnostics. No scoring, progress, badge, or Mentor mutation logic is changed.

## Changed files

Backend (paths relative to `HALO/src/main/java/com/ptc/halo/`):
- `service/ProfessorEngagementService.java`
- `controller/ProfessorEngagementController.java`
- `entity/LessonStudyDayEntity.java`
- `repository/LessonStudyDayRepository.java`
- `service/LessonStudyService.java`
- `controller/LessonStudyController.java`
- `repository/UserRepository.java`

Database/documentation: `HALO/database/learning-engagement-study-days.sql` and this file.

Frontend:
- `src/pages/professor/components/LearningEngagement.jsx`
- `src/pages/professor/components/StudentProgress.jsx`
- `src/pages/professor/styles/StudentProgress.module.css`
- `src/pages/student/components/LessonChat.jsx`
- `src/services/professor/professorService.js`
- `src/services/student/studentService.js`
- `src/utils/studentDiagnostics.js`

Backend tests: `ProfessorEngagementTest`, `LessonStudyTest`, `EngagementHttpTest`,
`ProfessorStudentScopeTest`, `AdminStudentPreviewTest`, `AdminStudentModeTest`.

Frontend tests: `tests/professor-progress-pagination.test.mjs`,
`tests/professor-navigation.test.mjs`, `tests/admin-student-mode.test.mjs`,
`tests/fixtures/backend-contract.json`.

## Reviews, attempts, and consistency

Apply `database/learning-engagement-reviews.sql` manually before deploying this extension (after the study-days migration). It has NOT been applied by this change. Stop writers and use ddl-auto=validate. Existing study history is retained; unknown historical review counts stay zero.

A review is a lesson open after a previous open with at least 30 minutes of inactivity. Each open updates the latest timestamp, so refreshes extend the inactivity window. The first open is never a review. Crossing midnight alone does not qualify. The per-Student transaction lock serializes recording. A legacy same-day row initializes its timestamp without a review; an earlier historical study day proves a prior visit.

Module activity uses a bounded page (default 20, maximum 100) at GET /api/professor/students/{studentId}/engagement/modules, and its Admin acting Professor equivalent. Three SQL queries validate eligibility, count modules, and retrieve page aggregates. Returned fields: moduleId, subjectName, weekNumber, title, reviewCount (all recorded reviews), reviewsMonth, assessmentAttempts. Attempt counts include both unfinished and submitted real attempts. Preview attempts/chat are ephemeral and cannot enter these aggregates.

Consistency uses distinct active calendar days from existing scoped lesson, assessment, completion, and meaningful Mentor records. Monday-start week: 3+ Consistent, 2 Moderate, 0-1 Low Activity. Current month: 8+ Consistent, 4-7 Moderate, 0-3 Low Activity. These are activity-to-date descriptions, not attainment scores or predictions. Early periods can naturally show Low Activity. The server timezone is returned by the API. Zero-activity eligible Students and modules remain visible.

Scope requires both Professor ownership and the selected Student's current year-level subject eligibility. The UI opens module activity on demand with independent loading/error/retry and paging; aborted/stale requests use existing useRemoteData protection. Quiz, progress, badge and Mentor business rules are unchanged.

Extension files: LessonStudyDayEntity.java, LessonStudyDayRepository.java, LessonStudyService.java, ProfessorEngagementService.java, ProfessorEngagementController.java, learning-engagement-reviews.sql, this document; professorService.js, LearningEngagement.jsx; LessonStudyTest.java, ProfessorEngagementTest.java, EngagementHttpTest.java, professor-progress-pagination.test.mjs, backend-contract.json.
