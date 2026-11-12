# HALO ERD Validation

## Source and safety

Inspected 2026-11-12. Explicit TCP `127.0.0.1:3307`, schema `halo_db`, server `DESKTOP-AHSCSMP`, version `10.4.32-MariaDB`. Client used `--no-defaults`; no production configuration was loaded. Only SELECT statements for server identity and information_schema metadata were issued. No row queries, startup, schema sync, migrations, integration tests, or external database/storage connections occurred.

## Coverage

| Check | Result |
|---|---|
| Actual / DBML base tables | 24 / 24 |
| Actual / documented columns | 174 / 174 |
| Primary keys | 24 |
| Composite primary keys | 0 |
| Unique constraints excluding PK | 19 |
| Composite unique constraints | 8 |
| Database FKs / DBML relationships | 31 / 31 |
| Composite FKs | 0 |
| Duplicate FK declarations | 0 |
| Missing annotated JPA FKs | 0 |
| Inferred application links excluded from edges | 6 (4 scalar, 2 JSON paths) |

The metadata extraction was repeated and compared exactly before documentation was written. Column order and ordered key membership are retained. Coverage checks compare generated declarations with the metadata, independently of grammar validation.

## Syntax validation

**DBML parser validation NOT PERFORMED:** `@dbml/core` and `@dbml/cli` were unavailable. No packages were installed. Syntax was reviewed against [official DBML documentation](https://dbml.dbdiagram.io/docs/). Import into dbdiagram.io for the remaining grammar/render check; no schema was uploaded during this task. Native enum types are quoted to retain exact allowed values. Generated expressions and safe defaults are notes, not executable migration definitions. This document is not a replacement for a schema backup. Non-unique performance indexes and CHECK constraints are outside the requested key inventory.

## Tables without FKs

No outgoing FKs: `password_reset_rate_limits`, `user_entity`.

No incoming or outgoing FKs: `password_reset_rate_limits`.

## Verified database foreign keys

Parent participation is optional: no FK requires every parent to have a child. Child minimum is zero for nullable FK columns, one otherwise. Unique child keys constrain parent-to-child to 0..1; other edges allow 0..many. Account roles and authorization are not enforced by these FKs.

| Constraint | Child columns | Referenced columns | Parent to child | Child to parent | Update | Delete |
|---|---|---|---|---|---|---|
| `FKc76fyqm3ut7w9l3vsoio4mm9f` | `activity_log_entity.(user_id)` | `user_entity.(id)` | 0..many | 0..1 | RESTRICT | RESTRICT |
| `fk_activity_acting_session` | `activity_log_entity.(acting_session_id)` | `admin_acting_session.(id)` | 0..many | 0..1 | RESTRICT | RESTRICT |
| `fk_activity_acting_target` | `activity_log_entity.(acting_target_id)` | `user_entity.(id)` | 0..many | 0..1 | RESTRICT | RESTRICT |
| `fk_acting_admin` | `admin_acting_session.(admin_id)` | `user_entity.(id)` | 0..many | 1 | RESTRICT | RESTRICT |
| `fk_acting_target` | `admin_acting_session.(target_id)` | `user_entity.(id)` | 0..many | 1 | RESTRICT | RESTRICT |
| `FK4tiy1ggtx63iix6dpall6bt0t` | `ai_learning_files.(ai_learning_module_id)` | `ai_learning_modules.(id)` | 0..many | 1 | RESTRICT | RESTRICT |
| `FKbiiwyh1qbureppsl4mw7jnpka` | `ai_learning_modules.(week_id)` | `weeks.(id)` | 0..1 | 1 | RESTRICT | RESTRICT |
| `FK2g2urk3okif3wciala8tas1ex` | `assessments.(module_id)` | `ai_learning_modules.(id)` | 0..many | 1 | RESTRICT | RESTRICT |
| `FK16b7ni1t3ynt7na31ng2ujett` | `assessment_attempts.(student_id)` | `user_entity.(id)` | 0..many | 1 | RESTRICT | RESTRICT |
| `FK82558btrx0bb6s1ryii2106ya` | `assessment_attempts.(assessment_id)` | `assessments.(id)` | 0..many | 1 | RESTRICT | RESTRICT |
| `FKljemcscn19ij7kysiqewaqp88` | `assessment_questions.(assessment_id)` | `assessments.(id)` | 0..many | 1 | RESTRICT | RESTRICT |
| `fk_lesson_revision_module` | `lesson_revisions.(module_id)` | `ai_learning_modules.(id)` | 0..1 | 1 | RESTRICT | RESTRICT |
| `fk_lesson_history_module` | `lesson_revision_history.(module_id)` | `ai_learning_modules.(id)` | 0..many | 1 | RESTRICT | RESTRICT |
| `fk_study_module` | `lesson_study_days.(module_id)` | `ai_learning_modules.(id)` | 0..many | 1 | RESTRICT | CASCADE |
| `fk_study_student` | `lesson_study_days.(student_id)` | `user_entity.(id)` | 0..many | 1 | RESTRICT | CASCADE |
| `FK9l1g2i28xpihrb8ev40ke28ej` | `mentor_messages.(session_id)` | `mentor_sessions.(id)` | 0..many | 1 | RESTRICT | RESTRICT |
| `FKewiirvaq8gs1050s6824oa98l` | `mentor_sessions.(ai_learning_module_id)` | `ai_learning_modules.(id)` | 0..many | 1 | RESTRICT | RESTRICT |
| `FKq7dvujqbyl7evsyqxhhnoiucm` | `mentor_sessions.(student_id)` | `user_entity.(id)` | 0..many | 1 | RESTRICT | RESTRICT |
| `FKay9wnwgaawpg5d1ho62kam7vb` | `password_reset_otps.(user_id)` | `user_entity.(id)` | 0..1 | 1 | RESTRICT | RESTRICT |
| `FKper8aj2lpm7ax4at5m0wv4a7b` | `professor_entity.(user_id)` | `user_entity.(id)` | 0..1 | 1 | RESTRICT | RESTRICT |
| `FK3mwu7ebon4vu6khiacuc7ycdr` | `professor_profile_entity.(user_id)` | `user_entity.(id)` | 0..1 | 1 | RESTRICT | RESTRICT |
| `FKl7pippsbiipty7wacay6ikn7u` | `student_answers.(attempt_id)` | `assessment_attempts.(id)` | 0..many | 1 | RESTRICT | RESTRICT |
| `FKso8bp2swsm46ynsqo4ci7a28k` | `student_answers.(question_id)` | `assessment_questions.(id)` | 0..many | 1 | RESTRICT | RESTRICT |
| `FKsskpnt4roiyft93pu0iw608f6` | `student_badges.(student_id)` | `user_entity.(id)` | 0..many | 1 | RESTRICT | RESTRICT |
| `FKltnys2bmlwhpdd8y09sg5ea81` | `student_module_progress.(module_id)` | `ai_learning_modules.(id)` | 0..many | 1 | RESTRICT | RESTRICT |
| `FKot2615mwq7jlbwdxa0t5doe9p` | `student_module_progress.(student_id)` | `user_entity.(id)` | 0..many | 1 | RESTRICT | RESTRICT |
| `FKem7pmm34qui17c7l3nibgu0qq` | `student_profile_entity.(user_id)` | `user_entity.(id)` | 0..1 | 1 | RESTRICT | RESTRICT |
| `fk_enrollment_student` | `student_subject_enrollments.(student_id)` | `user_entity.(id)` | 0..many | 1 | RESTRICT | RESTRICT |
| `fk_enrollment_subject` | `student_subject_enrollments.(subject_id)` | `subjects.(id)` | 0..many | 1 | RESTRICT | RESTRICT |
| `fk_subject_professor` | `subjects.(professor_id)` | `professor_entity.(id)` | 0..many | 0..1 | RESTRICT | RESTRICT |
| `FKnkrtyffpeq8ykfnw5myn8raco` | `weeks.(subject_id)` | `subjects.(id)` | 0..many | 1 | RESTRICT | RESTRICT |

## JPA cross-check

All 23 entity files under `HALO/src/main/java/com/ptc/halo/entity/` were read. No `@ManyToMany` or `@JoinTable` was found. Inverse collections `AiLearningModuleEntity.files`, `AssessmentEntity.questions`, and `MentorSessionEntity.messages` map existing child FKs; they do not produce extra edges.

| Entity field | Child column | Target | Result |
|---|---|---|---|
| `ActivityLogEntity.user` | `activity_log_entity.user_id` | `user_entity.id` | FK verified |
| `ActivityLogEntity.actingTarget` | `activity_log_entity.acting_target_id` | `user_entity.id` | FK verified |
| `ActivityLogEntity.actingSession` | `activity_log_entity.acting_session_id` | `admin_acting_session.id` | FK verified |
| `AdminActingSessionEntity.admin` | `admin_acting_session.admin_id` | `user_entity.id` | FK verified |
| `AdminActingSessionEntity.target` | `admin_acting_session.target_id` | `user_entity.id` | FK verified |
| `AiLearningFileEntity.module` | `ai_learning_files.ai_learning_module_id` | `ai_learning_modules.id` | FK verified |
| `AiLearningModuleEntity.week` | `ai_learning_modules.week_id` | `weeks.id` | FK verified |
| `AssessmentAttemptEntity.assessment` | `assessment_attempts.assessment_id` | `assessments.id` | FK verified |
| `AssessmentAttemptEntity.student` | `assessment_attempts.student_id` | `user_entity.id` | FK verified |
| `AssessmentEntity.module` | `assessments.module_id` | `ai_learning_modules.id` | FK verified |
| `AssessmentQuestionEntity.assessment` | `assessment_questions.assessment_id` | `assessments.id` | FK verified |
| `MentorMessageEntity.session` | `mentor_messages.session_id` | `mentor_sessions.id` | FK verified |
| `MentorSessionEntity.student` | `mentor_sessions.student_id` | `user_entity.id` | FK verified |
| `MentorSessionEntity.module` | `mentor_sessions.ai_learning_module_id` | `ai_learning_modules.id` | FK verified |
| `PasswordResetOtpEntity.user` | `password_reset_otps.user_id` | `user_entity.id` | FK verified |
| `ProfessorEntity.user` | `professor_entity.user_id` | `user_entity.id` | FK verified |
| `StudentAnswerEntity.attempt` | `student_answers.attempt_id` | `assessment_attempts.id` | FK verified |
| `StudentAnswerEntity.question` | `student_answers.question_id` | `assessment_questions.id` | FK verified |
| `StudentBadgeEntity.student` | `student_badges.student_id` | `user_entity.id` | FK verified |
| `StudentModuleProgressEntity.student` | `student_module_progress.student_id` | `user_entity.id` | FK verified |
| `StudentModuleProgressEntity.module` | `student_module_progress.module_id` | `ai_learning_modules.id` | FK verified |
| `StudentProfileEntity.user` | `student_profile_entity.user_id` | `user_entity.id` | FK verified |
| `StudentSubjectEnrollmentEntity.student` | `student_subject_enrollments.student_id` | `user_entity.id` | FK verified |
| `StudentSubjectEnrollmentEntity.subject` | `student_subject_enrollments.subject_id` | `subjects.id` | FK verified |
| `SubjectEntity.professor` | `subjects.professor_id` | `professor_entity.id` | FK verified |
| `WeekEntity.subject` | `weeks.subject_id` | `subjects.id` | FK verified |

Additional DB-enforced scalar references: `LessonRevisionEntity.moduleId`, `LessonRevisionHistoryEntity.moduleId`, `LessonStudyDayEntity.studentId/moduleId`. The unmapped `professor_profile_entity.user_id` also has a verified FK.

Differences retained in the ERD:

- `professor_profile_entity` has no current entity; its current use is unverified.
- `ai_learning_modules.uploaded_pdf` and `mentor_sessions.covered_concepts` are not in current entities.
- Generated columns `assessments.current_module_id`, `professor_entity.professor_id_normalized`, `user_entity.email_normalized` are database-managed. Their expressions appear in the dictionary/DBML notes.
- `ActivityLogEntity.action/createdAt` declare non-null, but the actual database columns are nullable. The ERD follows the database.
- No unique student/module pair constraint exists on `mentor_sessions` or `student_module_progress`; do not infer such a constraint from application intentions.

## Application-level links, excluded from DBML Ref

No missing database FK was found among the 26 owning `@JoinColumn` associations. The following scalar/JSON relationships are not annotated JPA associations and are not enforced by database FKs.

| Entity / field | Database column/path | Logical target | Evidence |
|---|---|---|---|
| `LessonRevisionEntity.revisedBy` | `lesson_revisions.revised_by` | `UserEntity` / `user_entity.id` | `LessonRevisionService.actor`, `start`, `publish`, `archivePublished` preserve the original actor ID. |
| `LessonRevisionEntity.publishedBy` | `lesson_revisions.published_by` | `UserEntity` / `user_entity.id` | `LessonRevisionService.actor`, `start`, `publish`, `archivePublished` preserve the original actor ID. |
| `LessonRevisionHistoryEntity.revisedBy` | `lesson_revision_history.revised_by` | `UserEntity` / `user_entity.id` | `LessonRevisionService.actor`, `start`, `publish`, `archivePublished` preserve the original actor ID. |
| `LessonRevisionHistoryEntity.publishedBy` | `lesson_revision_history.published_by` | `UserEntity` / `user_entity.id` | `LessonRevisionService.actor`, `start`, `publish`, `archivePublished` preserve the original actor ID. |
| `LessonRevisionEntity.payload` | `lesson_revisions.payload.files[].fileId` | `AiLearningFileEntity` / `ai_learning_files.id` | `LessonRevisionService.Attachment`, `publishedPayload`, `archivePublished`; snapshot references may outlive current attachment rows. |
| `LessonRevisionHistoryEntity.payload` | `lesson_revision_history.payload.files[].fileId` | `AiLearningFileEntity` / `ai_learning_files.id` | `LessonRevisionService.Attachment`, `publishedPayload`, `archivePublished`; snapshot references may outlive current attachment rows. |

Totals: 0 missing annotated JPA FKs; 4 scalar inferred actor links; 2 nested JSON attachment-reference paths (6 application-level links). Version numbers and storage paths are not separate database foreign keys. No current attachment-row existence is guaranteed by a historical JSON fileId.

## File scope

Only `docs/database/HALO_ERD.dbml`, `docs/database/HALO_DATA_DICTIONARY.md`, and `docs/database/HALO_ERD_VALIDATION.md` were created. Existing uncommitted application changes predated this documentation task. No application source, configuration, schema, records, permissions or foreign keys were changed. No commit, push or deployment. This is a local snapshot, not a claim about production Aiven.

## Independent output reconciliation

PASS: a second, read-only metadata query was compared against the saved files: exact table names, 174 ordered column names/types/nullability and dictionary entries, all 24 primary keys, all 19 named unique constraints including ordered composite members, and all 31 FK endpoints. No extra table, column or relationship declarations were found. This targeted structural checker is not a general DBML parser.

## Nullable foreign key notation correction (2026-11-12)

Rechecked only the four affected foreign keys using information_schema on local `127.0.0.1:3307`, `halo_db`, MariaDB `10.4.32-MariaDB`. All four columns have `IS_NULLABLE=YES` and retain their existing verified targets. Changed the DBML operator from `>` to `>?` to explicitly mark the referenced side optional:

- `activity_log_entity.user_id >? user_entity.id`
- `activity_log_entity.acting_target_id >? user_entity.id`
- `activity_log_entity.acting_session_id >? admin_acting_session.id`
- `subjects.professor_id >? professor_entity.id`

All column definitions, constraint names, targets and update/delete rules remain unchanged. An exact comparison confirmed that these four operator substitutions are the only ERD changes. Structural counts remain 24 tables, 174 columns, 24 primary keys, 19 unique constraints and 31 foreign key relationships, with no duplicate relationship names.

The user reported that the previous DBML rendered in dbdiagram.io with eight warnings associated with these four columns. This correction addresses the optionality notation; warning clearance has not been observed directly. An automated DBML parser remains unavailable, so manual re-import into dbdiagram.io is required to confirm the warnings are cleared. Only this report and HALO_ERD.dbml were modified in this correction; no database writes, production connections or application changes were performed.
