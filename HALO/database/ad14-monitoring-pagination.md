# AD-14 Admin monitoring pagination

Endpoints (ADMIN authorization unchanged):

- GET /api/admin/activity-logs?page=0&size=20&search=&type=MODULE&role=PROFESSOR
- GET /api/admin/professors/monitoring?page=0&size=20
- GET /api/admin/students/monitoring?page=0&size=20

All use zero-based `number`, `size`, `content`, `totalElements`, `totalPages`, matching the existing Spring Page convention. Default size is 20; the UI uses 25 for monitoring charts. HTTP sizes outside 1–100 and negative pages return structured 400 validation errors.

Activity search remains case-insensitive literal substring search on action or actor name. Category (`type`) and optional actor role are applied before pagination. Omitting role includes all roles, including SUPER_ADMIN. Ordering is createdAt DESC, id DESC; the actor is fetched in the same query. Mapping remains read-only transactional.

Monitoring content preserves the original row field names. Both responses additionally return `summary` (whole population, not just this page) and `highlights` (at most eight rows):

- Professors: summary total/active/moduleActivities; highlights most recently active. Content ordered moduleActivities DESC, userId ASC.
- Students: summary total/scored/averageScore/passedAssessments; highlights lowest-scoring students with submitted attempts. Content ordered latestAssessmentScore DESC, userId ASC. Latest attempt uses submittedAt then id to break ties. Scores are unchanged percentages.

Accounts lacking profiles remain excluded, as before. Scalar subqueries preserve independent counts without multiplying progress, badge, attempt or activity rows. Derived aggregate queries produce global totals without loading account collections. No schema changes.

Query pattern:
- Logs: formerly an unbounded list plus potential lazy actor queries; now at most content + count (2).
- Professors: formerly 1 + 3 per-account queries; now page + count + global summary + bounded recent list (at most 4).
- Students: formerly 1 + 5 per-account queries; now page + count + global summary + bounded lowest-score list (at most 4).

The frontend sends search/category/page to the server, retains cancellation-aware useRemoteData, and provides Previous/Next controls. Charts show a ranked page; whole-population metric cards and highlights retain their original meaning. Empty, loading, error and retry states remain available.

AdminMonitoringPagingTest measures prepared-statement counts before/after adding 30 accounts and checks zero entity loading for monitoring projections. Live database execution plans should still be assessed for production-sized data; constant query count does not imply constant database work.
