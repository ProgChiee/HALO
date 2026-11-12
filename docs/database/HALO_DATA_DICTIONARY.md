# HALO Data Dictionary

Local metadata snapshot: 2026-11-12; `127.0.0.1:3307`, `halo_db`, MariaDB `10.4.32-MariaDB`. No application records queried. Database metadata is authoritative; descriptions are based on entity code and field names, not data inspection. Unmapped legacy semantics are marked unverified.

Composite UNIQUE membership does not imply an individual column is unique. MariaDB nullable unique keys permit multiple NULLs. Defaults are metadata only; non-NULL/non-0/non-1 literals are omitted conservatively. Generated expressions are documented as notes, not executable DBML definitions.

## activity_log_entity

Security and academic activity audit events. Engine: InnoDB.

Entity: `HALO/src/main/java/com/ptc/halo/entity/ActivityLogEntity.java`.

| Column | Data type | Nullable | PK | FK / referenced column | Unique membership | Safe default | Description |
|---|---|---|---|---|---|---|---|
| `id` | `bigint(20)` | No | Yes | - | - | [unspecified] | Row identifier. |
| `action` | `varchar(255)` | Yes | No | - | - | NULL | Original audit description; no values inspected. Metadata default: NULL. |
| `created_at` | `datetime(6)` | Yes | No | - | - | NULL | Timestamp for created. Metadata default: NULL. |
| `user_id` | `bigint(20)` | Yes | No | `FKc76fyqm3ut7w9l3vsoio4mm9f`: `user_entity.id` | - | NULL | Reference to user_entity.id. Metadata default: NULL. |
| `activity_type` | `enum('ACCOUNT','ASSESSMENT','AUTH','BADGE','MODULE','PROGRESS')` | Yes | No | - | - | NULL | Audit activity category. Metadata default: NULL. |
| `acting_target_id` | `bigint(20)` | Yes | No | `fk_activity_acting_target`: `user_entity.id` | - | NULL | Reference to user_entity.id. Metadata default: NULL. |
| `acting_target_role` | `varchar(32)` | Yes | No | - | - | NULL | Audited target role. Metadata default: NULL. |
| `acting_session_id` | `varchar(36)` | Yes | No | `fk_activity_acting_session`: `admin_acting_session.id` | - | NULL | Reference to admin_acting_session.id. Metadata default: NULL. |
| `auth_event` | `varchar(64)` | Yes | No | - | - | NULL | Structured authentication or acting event type. Metadata default: NULL. |
| `auth_display` | `varchar(512)` | Yes | No | - | - | NULL | Authentication audit display metadata; no values inspected. Metadata default: NULL. |
| `auth_event_key` | `varchar(64)` | Yes | No | - | `uk_activity_auth_event_key` (auth_event_key) | NULL | Authentication audit deduplication key. Metadata default: NULL. |

## admin_acting_session

Time-limited administrator acting context and target identity. Engine: InnoDB.

Entity: `HALO/src/main/java/com/ptc/halo/entity/AdminActingSessionEntity.java`.

| Column | Data type | Nullable | PK | FK / referenced column | Unique membership | Safe default | Description |
|---|---|---|---|---|---|---|---|
| `id` | `varchar(36)` | No | Yes | - | - | [unspecified] | Row identifier. |
| `admin_id` | `bigint(20)` | No | No | `fk_acting_admin`: `user_entity.id` | - | [unspecified] | Reference to user_entity.id. |
| `target_id` | `bigint(20)` | No | No | `fk_acting_target`: `user_entity.id` | - | [unspecified] | Reference to user_entity.id. |
| `target_role` | `varchar(32)` | No | No | - | - | [unspecified] | Acting target role captured at entry. |
| `admin_token_version` | `bigint(20)` | No | No | - | - | [unspecified] | Original actor token version captured at entry. |
| `target_token_version` | `bigint(20)` | No | No | - | - | [unspecified] | Target token version captured at entry. |
| `created_at` | `datetime(6)` | No | No | - | - | [unspecified] | Timestamp for created. |
| `expires_at` | `datetime(6)` | No | No | - | - | [unspecified] | Timestamp for expires. |
| `revoked_at` | `datetime(6)` | Yes | No | - | - | NULL | Timestamp for revoked. Metadata default: NULL. |

## ai_learning_files

Learning attachment metadata and storage references, not original file bytes. Engine: InnoDB.

Entity: `HALO/src/main/java/com/ptc/halo/entity/AiLearningFileEntity.java`.

| Column | Data type | Nullable | PK | FK / referenced column | Unique membership | Safe default | Description |
|---|---|---|---|---|---|---|---|
| `id` | `bigint(20)` | No | Yes | - | - | [unspecified] | Row identifier. |
| `file_path` | `varchar(255)` | No | No | - | - | [unspecified] | Storage object reference or historical local path; values not inspected. |
| `file_type` | `varchar(255)` | No | No | - | - | [unspecified] | Learning material MIME type. |
| `original_file_name` | `varchar(255)` | No | No | - | - | [unspecified] | Original upload filename. |
| `stored_file_name` | `varchar(255)` | No | No | - | - | [unspecified] | Storage-assigned filename. |
| `ai_learning_module_id` | `bigint(20)` | No | No | `FK4tiy1ggtx63iix6dpall6bt0t`: `ai_learning_modules.id` | - | [unspecified] | Reference to ai_learning_modules.id. |

## ai_learning_modules

Week-scoped generated and approved lesson content. Engine: InnoDB.

Entity: `HALO/src/main/java/com/ptc/halo/entity/AiLearningModuleEntity.java`.

| Column | Data type | Nullable | PK | FK / referenced column | Unique membership | Safe default | Description |
|---|---|---|---|---|---|---|---|
| `id` | `bigint(20)` | No | Yes | - | - | [unspecified] | Row identifier. |
| `ai_notes` | `longtext` | Yes | No | - | - | NULL | Professor-provided AI generation notes. Metadata default: NULL. |
| `generated_examples` | `longtext` | Yes | No | - | - | NULL | Generated lesson examples. Metadata default: NULL. |
| `generated_knowledge` | `longtext` | Yes | No | - | - | NULL | Generated lesson knowledge content. Metadata default: NULL. |
| `generated_objectives` | `longtext` | Yes | No | - | - | NULL | Generated learning objectives. Metadata default: NULL. |
| `generated_summary` | `longtext` | Yes | No | - | - | NULL | Generated lesson summary. Metadata default: NULL. |
| `lesson_text` | `longtext` | Yes | No | - | - | NULL | Generated/published lesson body. Metadata default: NULL. |
| `status` | `enum('APPROVED','DECLINED','PENDING')` | Yes | No | - | - | NULL | Record lifecycle state; see enum type if constrained. Metadata default: NULL. |
| `uploaded_pdf` | `longtext` | Yes | No | - | - | NULL | Legacy PDF-related field absent from current entity; precise meaning unverified. Metadata default: NULL. |
| `youtube_link` | `varchar(255)` | Yes | No | - | - | NULL | Supplementary video URL. Metadata default: NULL. |
| `week_id` | `bigint(20)` | No | No | `FKbiiwyh1qbureppsl4mw7jnpka`: `weeks.id` | `UKnsb9jv0dl6yqg7x7warv9vehs` (week_id) | [unspecified] | Reference to weeks.id. |
| `ai_generation_status` | `enum('COMPLETED','DECLINED','FAILED','PENDING')` | Yes | No | - | - | NULL | AI generation lifecycle status. Metadata default: NULL. |
| `generation_token` | `varchar(36)` | Yes | No | - | - | NULL | AI generation ownership/correlation identifier; values not inspected. Metadata default: NULL. |
| `version` | `bigint(20)` | No | No | - | - | [unspecified] | Optimistic concurrency version. |
| `assessment_needs_review` | `tinyint(1)` | No | No | - | - | 0 | Flags quiz review after lesson changes. Metadata default: 0. |

## assessments

Versioned module quizzes and approval lifecycle. Engine: InnoDB.

Entity: `HALO/src/main/java/com/ptc/halo/entity/AssessmentEntity.java`.

| Column | Data type | Nullable | PK | FK / referenced column | Unique membership | Safe default | Description |
|---|---|---|---|---|---|---|---|
| `id` | `bigint(20)` | No | Yes | - | - | [unspecified] | Row identifier. |
| `created_at` | `datetime(6)` | No | No | - | - | [unspecified] | Timestamp for created. |
| `passing_score` | `int(11)` | No | No | - | - | [unspecified] | Assessment passing threshold. |
| `status` | `varchar(20)` | No | No | - | - | [unspecified] | Record lifecycle state; see enum type if constrained. |
| `title` | `varchar(255)` | No | No | - | - | [unspecified] | Display title. |
| `module_id` | `bigint(20)` | No | No | `FK2g2urk3okif3wciala8tas1ex`: `ai_learning_modules.id` | `uk_assessment_module_version` (module_id, quiz_version) | [unspecified] | Reference to ai_learning_modules.id. |
| `quiz_version` | `int(11)` | No | No | - | `uk_assessment_module_version` (module_id, quiz_version) | 1 | Assessment version within module. Metadata default: 1. |
| `source_module_version` | `bigint(20)` | Yes | No | - | - | NULL | Module version used for assessment generation. Metadata default: NULL. |
| `approved_at` | `datetime(6)` | Yes | No | - | - | NULL | Timestamp for approved. Metadata default: NULL. |
| `current_module_id` | `bigint(20)` | Yes | No | - | `uk_assessment_current_module` (current_module_id) | NULL | Generated key for current assessment uniqueness; not an independent FK. STORED GENERATED: case when `status` in ('AVAILABLE','INACTIVE') then `module_id` else NULL end. Metadata default: NULL. |

## assessment_attempts

Student attempts, submission timestamps and results. Engine: InnoDB.

Entity: `HALO/src/main/java/com/ptc/halo/entity/AssessmentAttemptEntity.java`.

| Column | Data type | Nullable | PK | FK / referenced column | Unique membership | Safe default | Description |
|---|---|---|---|---|---|---|---|
| `id` | `bigint(20)` | No | Yes | - | - | [unspecified] | Row identifier. |
| `passed` | `bit(1)` | No | No | - | - | [unspecified] | Assessment pass/fail result. |
| `score` | `int(11)` | No | No | - | - | [unspecified] | Recorded assessment score. |
| `started_at` | `datetime(6)` | No | No | - | - | [unspecified] | Timestamp for started. |
| `submitted_at` | `datetime(6)` | Yes | No | - | - | NULL | Timestamp for submitted. Metadata default: NULL. |
| `assessment_id` | `bigint(20)` | No | No | `FK82558btrx0bb6s1ryii2106ya`: `assessments.id` | - | [unspecified] | Reference to assessments.id. |
| `student_id` | `bigint(20)` | No | No | `FK16b7ni1t3ynt7na31ng2ujett`: `user_entity.id` | - | [unspecified] | Reference to user_entity.id. |

## assessment_questions

Ordered multiple-choice assessment questions. Engine: InnoDB.

Entity: `HALO/src/main/java/com/ptc/halo/entity/AssessmentQuestionEntity.java`.

| Column | Data type | Nullable | PK | FK / referenced column | Unique membership | Safe default | Description |
|---|---|---|---|---|---|---|---|
| `id` | `bigint(20)` | No | Yes | - | - | [unspecified] | Row identifier. |
| `correct_answer` | `varchar(1)` | No | No | - | - | [unspecified] | Correct option identifier; no values inspected. |
| `optiona` | `varchar(255)` | No | No | - | - | [unspecified] | Answer option A. |
| `optionb` | `varchar(255)` | No | No | - | - | [unspecified] | Answer option B. |
| `optionc` | `varchar(255)` | No | No | - | - | [unspecified] | Answer option C. |
| `optiond` | `varchar(255)` | No | No | - | - | [unspecified] | Answer option D. |
| `question_number` | `int(11)` | No | No | - | - | [unspecified] | Question ordering number. |
| `question_text` | `longtext` | No | No | - | - | [unspecified] | Question body. |
| `assessment_id` | `bigint(20)` | No | No | `FKljemcscn19ij7kysiqewaqp88`: `assessments.id` | - | [unspecified] | Reference to assessments.id. |

## lesson_revisions

Current working revision slot per module. Engine: InnoDB.

Entity: `HALO/src/main/java/com/ptc/halo/entity/LessonRevisionEntity.java`.

| Column | Data type | Nullable | PK | FK / referenced column | Unique membership | Safe default | Description |
|---|---|---|---|---|---|---|---|
| `module_id` | `bigint(20)` | No | Yes | `fk_lesson_revision_module`: `ai_learning_modules.id` | - | [unspecified] | Reference to ai_learning_modules.id. |
| `version` | `bigint(20)` | No | No | - | - | [unspecified] | Optimistic concurrency version. |
| `base_version` | `bigint(20)` | No | No | - | - | [unspecified] | Base module version for a revision. |
| `revision_number` | `int(11)` | No | No | - | - | [unspecified] | Revision number within module. |
| `state` | `varchar(16)` | No | No | - | - | [unspecified] | Working-revision lifecycle state. |
| `payload` | `longtext` | No | No | - | - | [unspecified] | Serialized lesson snapshot including attachment metadata. |
| `revised_by` | `bigint(20)` | No | No | - | - | [unspecified] | Revising user ID; application-level reference without a database FK. |
| `revised_at` | `datetime(6)` | No | No | - | - | [unspecified] | Timestamp for revised. |
| `published_by` | `bigint(20)` | Yes | No | - | - | NULL | Publishing user ID; application-level reference without a database FK. Metadata default: NULL. |
| `published_at` | `datetime(6)` | Yes | No | - | - | NULL | Timestamp for published. Metadata default: NULL. |

## lesson_revision_history

Immutable published revision snapshots. Engine: InnoDB.

Entity: `HALO/src/main/java/com/ptc/halo/entity/LessonRevisionHistoryEntity.java`.

| Column | Data type | Nullable | PK | FK / referenced column | Unique membership | Safe default | Description |
|---|---|---|---|---|---|---|---|
| `id` | `bigint(20)` | No | Yes | - | - | [unspecified] | Row identifier. |
| `module_id` | `bigint(20)` | No | No | `fk_lesson_history_module`: `ai_learning_modules.id` | `uq_lesson_history_version` (module_id, revision_number) | [unspecified] | Reference to ai_learning_modules.id. |
| `revision_number` | `int(11)` | No | No | - | `uq_lesson_history_version` (module_id, revision_number) | [unspecified] | Revision number within module. |
| `snapshot_kind` | `varchar(24)` | No | No | - | - | [unspecified] | Revision snapshot classification. |
| `revision_version` | `bigint(20)` | Yes | No | - | - | NULL | Captured working-revision version. Metadata default: NULL. |
| `base_version` | `bigint(20)` | Yes | No | - | - | NULL | Base module version for a revision. Metadata default: NULL. |
| `published_module_version` | `bigint(20)` | Yes | No | - | - | NULL | Published module version in snapshot. Metadata default: NULL. |
| `payload` | `longtext` | No | No | - | - | [unspecified] | Serialized lesson snapshot including attachment metadata. |
| `revised_by` | `bigint(20)` | Yes | No | - | - | NULL | Revising user ID; application-level reference without a database FK. Metadata default: NULL. |
| `revised_at` | `datetime(6)` | Yes | No | - | - | NULL | Timestamp for revised. Metadata default: NULL. |
| `published_by` | `bigint(20)` | Yes | No | - | - | NULL | Publishing user ID; application-level reference without a database FK. Metadata default: NULL. |
| `published_at` | `datetime(6)` | Yes | No | - | - | NULL | Timestamp for published. Metadata default: NULL. |
| `recorded_at` | `datetime(6)` | No | No | - | - | [unspecified] | Timestamp for recorded. |

## lesson_study_days

Per-student/module/calendar-day study and review counters. Engine: InnoDB.

Entity: `HALO/src/main/java/com/ptc/halo/entity/LessonStudyDayEntity.java`.

| Column | Data type | Nullable | PK | FK / referenced column | Unique membership | Safe default | Description |
|---|---|---|---|---|---|---|---|
| `id` | `bigint(20)` | No | Yes | - | - | [unspecified] | Row identifier. |
| `student_id` | `bigint(20)` | No | No | `fk_study_student`: `user_entity.id` | `uk_lesson_study_day` (student_id, module_id, study_date) | [unspecified] | Reference to user_entity.id. |
| `module_id` | `bigint(20)` | No | No | `fk_study_module`: `ai_learning_modules.id` | `uk_lesson_study_day` (student_id, module_id, study_date) | [unspecified] | Reference to ai_learning_modules.id. |
| `study_date` | `date` | No | No | - | `uk_lesson_study_day` (student_id, module_id, study_date) | [unspecified] | Calendar study date. |
| `last_opened_at` | `datetime(6)` | Yes | No | - | - | NULL | Timestamp for last opened. Metadata default: NULL. |
| `review_count` | `bigint(20)` | No | No | - | - | 0 | Lesson review count. Metadata default: 0. |

## mentor_messages

Student and HALO conversation messages and request reconciliation. Engine: InnoDB.

Entity: `HALO/src/main/java/com/ptc/halo/entity/MentorMessageEntity.java`.

| Column | Data type | Nullable | PK | FK / referenced column | Unique membership | Safe default | Description |
|---|---|---|---|---|---|---|---|
| `id` | `bigint(20)` | No | Yes | - | - | [unspecified] | Row identifier. |
| `created_at` | `datetime(6)` | No | No | - | - | [unspecified] | Timestamp for created. |
| `message` | `longtext` | No | No | - | - | [unspecified] | Mentor message text; no values inspected. |
| `sender` | `enum('HALO','STUDENT')` | No | No | - | `uk_mentor_request_sender` (session_id, request_id, sender) | [unspecified] | HALO or STUDENT speaker. |
| `session_id` | `bigint(20)` | No | No | `FK9l1g2i28xpihrb8ev40ke28ej`: `mentor_sessions.id` | `uk_mentor_request_sender` (session_id, request_id, sender) | [unspecified] | Reference to mentor_sessions.id. |
| `request_id` | `varchar(36)` | Yes | No | - | `uk_mentor_request_sender` (session_id, request_id, sender) | NULL | Message-exchange reconciliation request ID. Metadata default: NULL. |

## mentor_sessions

Student conversations scoped to a module. Engine: InnoDB.

Entity: `HALO/src/main/java/com/ptc/halo/entity/MentorSessionEntity.java`.

| Column | Data type | Nullable | PK | FK / referenced column | Unique membership | Safe default | Description |
|---|---|---|---|---|---|---|---|
| `id` | `bigint(20)` | No | Yes | - | - | [unspecified] | Row identifier. |
| `last_activity_at` | `datetime(6)` | Yes | No | - | - | NULL | Timestamp for last activity. Metadata default: NULL. |
| `started_at` | `datetime(6)` | No | No | - | - | [unspecified] | Timestamp for started. |
| `ai_learning_module_id` | `bigint(20)` | No | No | `FKewiirvaq8gs1050s6824oa98l`: `ai_learning_modules.id` | - | [unspecified] | Reference to ai_learning_modules.id. |
| `student_id` | `bigint(20)` | No | No | `FKq7dvujqbyl7evsyqxhhnoiucm`: `user_entity.id` | - | [unspecified] | Reference to user_entity.id. |
| `covered_concepts` | `longtext` | Yes | No | - | - | NULL | Legacy concept field absent from current entity; precise meaning unverified. Metadata default: NULL. |
| `progress_status` | `enum('COMPLETED','LEARNING','NOT_STARTED','READY_FOR_ASSESSMENT')` | No | No | - | - | [unspecified] | Mentor learning lifecycle status. |

## password_reset_otps

Password-reset verifier, expiry and attempt tracking. Engine: InnoDB.

Entity: `HALO/src/main/java/com/ptc/halo/entity/PasswordResetOtpEntity.java`.

| Column | Data type | Nullable | PK | FK / referenced column | Unique membership | Safe default | Description |
|---|---|---|---|---|---|---|---|
| `id` | `bigint(20)` | No | Yes | - | - | [unspecified] | Row identifier. |
| `created_at` | `datetime(6)` | No | No | - | - | [unspecified] | Timestamp for created. |
| `expires_at` | `datetime(6)` | No | No | - | - | [unspecified] | Timestamp for expires. |
| `otp` | `varchar(255)` | No | No | - | - | [unspecified] | Versioned keyed reset-code verifier (entity comment); no values inspected. |
| `user_id` | `bigint(20)` | No | No | `FKay9wnwgaawpg5d1ho62kam7vb`: `user_entity.id` | `UK54nf04xn0b2a3xxlysc84u9d7` (user_id) | [unspecified] | Reference to user_entity.id. |
| `failed_attempts` | `int(11)` | No | No | - | - | 0 | Failed reset-verification counter. Metadata default: 0. |

## password_reset_rate_limits

Password-reset admission-throttling buckets. Engine: InnoDB.

Entity: `HALO/src/main/java/com/ptc/halo/entity/PasswordResetRateLimitEntity.java`.

| Column | Data type | Nullable | PK | FK / referenced column | Unique membership | Safe default | Description |
|---|---|---|---|---|---|---|---|
| `bucket_key` | `varchar(64)` | No | Yes | - | - | [unspecified] | Throttle bucket identifier; no values inspected. |
| `window_started_at` | `datetime(6)` | No | No | - | - | [unspecified] | Timestamp for window started. |
| `event_count` | `int(11)` | No | No | - | - | [unspecified] | Rate-limit event counter. |
| `next_allowed_at` | `datetime(6)` | Yes | No | - | - | NULL | Timestamp for next allowed. Metadata default: NULL. |

## professor_entity

Professor identity linked to a user account. Engine: InnoDB.

Entity: `HALO/src/main/java/com/ptc/halo/entity/ProfessorEntity.java`.

| Column | Data type | Nullable | PK | FK / referenced column | Unique membership | Safe default | Description |
|---|---|---|---|---|---|---|---|
| `id` | `bigint(20)` | No | Yes | - | - | [unspecified] | Row identifier. |
| `professor_id` | `varchar(255)` | Yes | No | - | - | NULL | Institutional professor identifier when not a foreign key. Metadata default: NULL. |
| `user_id` | `bigint(20)` | No | No | `FKper8aj2lpm7ax4at5m0wv4a7b`: `user_entity.id` | `UKpo9e1orfoqpgh1tlsvm7imw3j` (user_id) | [unspecified] | Reference to user_entity.id. |
| `professor_id_normalized` | `varbinary(1020)` | Yes | No | - | `uk_professor_id_normalized` (professor_id_normalized) | NULL | Generated binary normalized professor identifier for uniqueness. VIRTUAL GENERATED: convert(trim(`professor_id`) using binary). Metadata default: NULL. |

## professor_profile_entity

Professor-profile-shaped table; no current JPA mapping, current purpose unverified. Engine: InnoDB.

No current entity found; actual database table retained.

| Column | Data type | Nullable | PK | FK / referenced column | Unique membership | Safe default | Description |
|---|---|---|---|---|---|---|---|
| `id` | `bigint(20)` | No | Yes | - | - | [unspecified] | Row identifier. |
| `professor_id` | `varchar(255)` | Yes | No | - | - | NULL | Institutional professor identifier when not a foreign key. Metadata default: NULL. |
| `user_id` | `bigint(20)` | No | No | `FK3mwu7ebon4vu6khiacuc7ycdr`: `user_entity.id` | `UKnst7732k7s5wqidj5eo69mn93` (user_id) | [unspecified] | Reference to user_entity.id. |

## student_answers

Student-selected answers within assessment attempts. Engine: InnoDB.

Entity: `HALO/src/main/java/com/ptc/halo/entity/StudentAnswerEntity.java`.

| Column | Data type | Nullable | PK | FK / referenced column | Unique membership | Safe default | Description |
|---|---|---|---|---|---|---|---|
| `id` | `bigint(20)` | No | Yes | - | - | [unspecified] | Row identifier. |
| `answer` | `varchar(1)` | No | No | - | - | [unspecified] | Student-selected option; no values inspected. |
| `attempt_id` | `bigint(20)` | No | No | `FKl7pippsbiipty7wacay6ikn7u`: `assessment_attempts.id` | `uk_student_answer_attempt_question` (attempt_id, question_id) | [unspecified] | Reference to assessment_attempts.id. |
| `question_id` | `bigint(20)` | No | No | `FKso8bp2swsm46ynsqo4ci7a28k`: `assessment_questions.id` | `uk_student_answer_attempt_question` (attempt_id, question_id) | [unspecified] | Reference to assessment_questions.id. |

## student_badges

Earned student badges. Engine: InnoDB.

Entity: `HALO/src/main/java/com/ptc/halo/entity/StudentBadgeEntity.java`.

| Column | Data type | Nullable | PK | FK / referenced column | Unique membership | Safe default | Description |
|---|---|---|---|---|---|---|---|
| `id` | `bigint(20)` | No | Yes | - | - | [unspecified] | Row identifier. |
| `badge_name` | `varchar(255)` | No | No | - | - | [unspecified] | Earned badge display label. |
| `badge_type` | `enum('ASSESSMENT_ACE','COMEBACK_STRONGER','DEDICATED_LEARNER','FIRST_STEP','FIRST_TRY','GETTING_STARTED','MODULE_FINISHER','HALO_ACHIEVER','KNOWLEDGE_SEEKER','NEVER_GIVE_UP','PERFECT_SCORE','SUBJECT_MASTER')` | No | No | - | `UKfetyiscxxfddulx4a41fsq4x0` (student_id, badge_type) | [unspecified] | Badge classification. |
| `description` | `varchar(255)` | No | No | - | - | [unspecified] | Descriptive text for the record. |
| `earned_at` | `datetime(6)` | No | No | - | - | [unspecified] | Timestamp for earned. |
| `student_id` | `bigint(20)` | No | No | `FKsskpnt4roiyft93pu0iw608f6`: `user_entity.id` | `UKfetyiscxxfddulx4a41fsq4x0` (student_id, badge_type) | [unspecified] | Reference to user_entity.id. |

## student_module_progress

Student module completion records. Engine: InnoDB.

Entity: `HALO/src/main/java/com/ptc/halo/entity/StudentModuleProgressEntity.java`.

| Column | Data type | Nullable | PK | FK / referenced column | Unique membership | Safe default | Description |
|---|---|---|---|---|---|---|---|
| `id` | `bigint(20)` | No | Yes | - | - | [unspecified] | Row identifier. |
| `completed` | `bit(1)` | No | No | - | - | [unspecified] | Module completion flag. |
| `completed_at` | `datetime(6)` | Yes | No | - | - | NULL | Timestamp for completed. Metadata default: NULL. |
| `module_id` | `bigint(20)` | No | No | `FKltnys2bmlwhpdd8y09sg5ea81`: `ai_learning_modules.id` | - | [unspecified] | Reference to ai_learning_modules.id. |
| `student_id` | `bigint(20)` | No | No | `FKot2615mwq7jlbwdxa0t5doe9p`: `user_entity.id` | - | [unspecified] | Reference to user_entity.id. |

## student_profile_entity

Student academic ID, section and year level. Engine: InnoDB.

Entity: `HALO/src/main/java/com/ptc/halo/entity/StudentProfileEntity.java`.

| Column | Data type | Nullable | PK | FK / referenced column | Unique membership | Safe default | Description |
|---|---|---|---|---|---|---|---|
| `id` | `bigint(20)` | No | Yes | - | - | [unspecified] | Row identifier. |
| `section` | `varchar(255)` | Yes | No | - | - | NULL | Student academic section. Metadata default: NULL. |
| `student_id` | `varchar(255)` | No | No | - | `UKp07cbiv7jbfpf78stw4x5hx44` (student_id) | [unspecified] | Institutional student identifier when not a foreign key. |
| `year_level` | `enum('FIRST_YEAR','SECOND_YEAR')` | Yes | No | - | - | NULL | Academic year-level classification. Metadata default: NULL. |
| `user_id` | `bigint(20)` | No | No | `FKem7pmm34qui17c7l3nibgu0qq`: `user_entity.id` | `UK9b80un6g4jv3asn4qjxohx626` (user_id) | [unspecified] | Reference to user_entity.id. |

## student_subject_enrollments

Active/inactive student-subject membership lifecycle. Engine: InnoDB.

Entity: `HALO/src/main/java/com/ptc/halo/entity/StudentSubjectEnrollmentEntity.java`.

| Column | Data type | Nullable | PK | FK / referenced column | Unique membership | Safe default | Description |
|---|---|---|---|---|---|---|---|
| `id` | `bigint(20)` | No | Yes | - | - | [unspecified] | Row identifier. |
| `student_id` | `bigint(20)` | No | No | `fk_enrollment_student`: `user_entity.id` | `uk_student_subject_enrollment` (student_id, subject_id) | [unspecified] | Reference to user_entity.id. |
| `subject_id` | `bigint(20)` | No | No | `fk_enrollment_subject`: `subjects.id` | `uk_student_subject_enrollment` (student_id, subject_id) | [unspecified] | Reference to subjects.id. |
| `recorded_at_epoch_ms` | `bigint(20)` | No | No | - | - | [unspecified] | Enrollment recording time in epoch milliseconds. |
| `source` | `varchar(32)` | No | No | - | - | [unspecified] | Enrollment provenance label. |
| `active` | `tinyint(1)` | No | No | - | - | 1 | Active enrollment membership flag. Metadata default: 1. |
| `unenrolled_at_epoch_ms` | `bigint(20)` | Yes | No | - | - | NULL | Unenrollment time in epoch milliseconds. Metadata default: NULL. |

## subjects

Subject catalogue, academic eligibility and professor ownership. Engine: InnoDB.

Entity: `HALO/src/main/java/com/ptc/halo/entity/SubjectEntity.java`.

| Column | Data type | Nullable | PK | FK / referenced column | Unique membership | Safe default | Description |
|---|---|---|---|---|---|---|---|
| `id` | `bigint(20)` | No | Yes | - | - | [unspecified] | Row identifier. |
| `description` | `text` | Yes | No | - | - | NULL | Descriptive text for the record. Metadata default: NULL. |
| `subject_code` | `varchar(255)` | No | No | - | `UKqt734ivq9gq4yo4p1j1lhhk8l` (subject_code) | [unspecified] | Subject catalogue code. |
| `subject_name` | `varchar(255)` | No | No | - | - | [unspecified] | Subject display name. |
| `year_level` | `enum('FIRST_YEAR','SECOND_YEAR')` | Yes | No | - | - | NULL | Academic year-level classification. Metadata default: NULL. |
| `professor_id` | `bigint(20)` | Yes | No | `fk_subject_professor`: `professor_entity.id` | - | NULL | Reference to professor_entity.id. Metadata default: NULL. |

## user_entity

Shared account identity, authentication state and role. Engine: InnoDB.

Entity: `HALO/src/main/java/com/ptc/halo/entity/UserEntity.java`.

| Column | Data type | Nullable | PK | FK / referenced column | Unique membership | Safe default | Description |
|---|---|---|---|---|---|---|---|
| `id` | `bigint(20)` | No | Yes | - | - | [unspecified] | Row identifier. |
| `email` | `varchar(255)` | Yes | No | - | - | NULL | Account email address. Metadata default: NULL. |
| `name` | `varchar(255)` | Yes | No | - | - | NULL | Account display name. Metadata default: NULL. |
| `password` | `varchar(255)` | Yes | No | - | - | NULL | Account password hash field; no values inspected. Metadata default: NULL. |
| `role` | `enum('ADMIN','PROFESSOR','STUDENT','SUPER_ADMIN')` | Yes | No | - | - | NULL | Authorization role. Metadata default: NULL. |
| `status` | `enum('ACTIVE','BLOCKED','INACTIVE','ARCHIVED')` | Yes | No | - | - | NULL | Record lifecycle state; see enum type if constrained. Metadata default: NULL. |
| `must_change_password` | `tinyint(1)` | No | No | - | - | 0 | Mandatory password-change flag. Metadata default: 0. |
| `token_version` | `bigint(20)` | No | No | - | - | 0 | Account token revocation version. Metadata default: 0. |
| `email_normalized` | `varchar(255)` | Yes | No | - | `uk_user_email_normalized` (email_normalized) | NULL | Generated normalized account email for uniqueness. VIRTUAL GENERATED: lcase(trim(`email`)). Metadata default: NULL. |
| `previous_status_before_archive` | `varchar(16)` | Yes | No | - | - | NULL | Account status retained for restoration. Metadata default: NULL. |
| `has_successful_login` | `tinyint(1)` | Yes | No | - | - | NULL | Nullable successful-login marker; NULL is legacy/unknown. Metadata default: NULL. |

## weeks

Ordered subject curriculum weeks. Engine: InnoDB.

Entity: `HALO/src/main/java/com/ptc/halo/entity/WeekEntity.java`.

| Column | Data type | Nullable | PK | FK / referenced column | Unique membership | Safe default | Description |
|---|---|---|---|---|---|---|---|
| `id` | `bigint(20)` | No | Yes | - | - | [unspecified] | Row identifier. |
| `title` | `varchar(255)` | Yes | No | - | - | NULL | Display title. Metadata default: NULL. |
| `week_number` | `int(11)` | Yes | No | - | `uk_week_subject_number` (subject_id, week_number) | NULL | Week ordering within subject. Metadata default: NULL. |
| `subject_id` | `bigint(20)` | No | No | `FKnkrtyffpeq8ykfnw5myn8raco`: `subjects.id` | `uk_week_subject_number` (subject_id, week_number) | [unspecified] | Reference to subjects.id. |

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

See [validation and JPA differences](HALO_ERD_VALIDATION.md).
