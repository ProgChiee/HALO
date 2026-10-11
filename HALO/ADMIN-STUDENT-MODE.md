# Admin Student Mode — Phase 4

All endpoints below have prefix /api/admin/acting/student and require the real Admin JWT plus X-Acting-Session (UUID).
Create a STUDENT session using the existing POST /api/admin/acting/sessions with targetUserId and targetRole: STUDENT.
No Student principal or JWT is issued. Query/body Student IDs cannot override the session target.

| Method | Route | Existing response |
| --- | --- | --- |
| GET | /dashboard | StudentDashboardResponse |
| GET | /subjects | StudentSubjectResponse[] |
| GET | /subjects/{id}/weeks | StudentWeekAccessResponse[] |
| GET | /ai-learning-modules/week/{id} | AiLearningModuleResponse |
| GET | /progress | StudentModuleProgressResponse[] |
| GET | /badges | StudentBadgeResponse[] |
| GET | /profile | StudentProfileResponse (read only) |
| GET | /assessment/{id} | AssessmentResponse; id is module ID |
| POST | /assessment/start/{id} | AssessmentAttemptResponse; id is module ID |
| POST | /assessment/submit/{id} | AssessmentResultResponse; id is attempt ID; existing StudentAnswerRequest body |
| GET | /assessment/result/{id} | Saved result/review; id is attempt ID |
| GET | /assessment/attempts/{id} | Attempt history; id is module ID |
| GET | /assessment/status/{id} | Assessment status; id is module ID |
| POST | /mentor/open/{id} | Initial bounded conversation; id is module ID |
| POST | /mentor/message/{id} | MentorSessionResponse; id is Mentor session ID; existing message/requestId body |
| GET | /mentor/message/{id}/request/{requestId} | Saved exchange for reconciliation |
| GET | /mentor/session/{id}?beforeId=... | Bounded older conversation window |

Use the existing UUID requestId for Mentor retries/reconciliation. No automatic POST retry is added.
Quiz resubmission retains ASSESSMENT_ALREADY_SUBMITTED (409); recover the saved result using the result endpoint.
There is no assessment-generation or credential/password mutation route.

## Transactions and audit

AdminStudentModeService validates the Admin/session/Student role, active status, token versions, expiry/revocation,
and Student profile in a short transaction. Quiz work joins that transaction and retains the Student lifecycle lock.
All quiz/progress/badge audits carry the Admin actor, Student target, and acting session; audit failure rolls back the mutation.

Mentor captures the Student in a short transaction. Server-only validation callbacks run again inside each capture/final
transaction, holding the acting-session lock only during database work. Indexing and AI run outside transactions.
The final phase checks the session again, then uses existing Student access, material version, module lock and request-key logic.
Only newly persisted sessions/exchanges are audited. Logs contain resource IDs, never answers or chat bodies.
Normal Student entry points use no-op acting callbacks and retain their original audit behavior.

No new database migration is required. Phase 1 acting-session/provenance schema must already exist.
No frontend code is added in Phase 4.

## Verification

AdminStudentModeTest uses isolated H2 persistence with real services for ownership, transactions, audits,
concurrency, scoped reads and conversation paging. Only external material/AI responses are mocked.
AdminStudentModeHttpTest exercises JWT/role/header/validation handling and denial of normal Student routes to Admin.
Existing assessment, badge, progression, Mentor, acting-session and Professor Mode regressions are also run.
Live Gemini/MySQL deployment testing is separate from these automated checks.
