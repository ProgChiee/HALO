-- Manual migration for MySQL 8 / MariaDB 10.4. NOT applied by the application.
-- Select the HALO database and stop application writers before deploying this change.
-- Use ddl-auto=validate for controlled deployment. Existing data is never rewritten.
-- Run this inspection first; if the table exists, stop and inspect instead of rerunning.
SELECT TABLE_NAME FROM information_schema.TABLES
WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lesson_study_days';
CREATE TABLE lesson_study_days (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    module_id BIGINT NOT NULL,
    study_date DATE NOT NULL,
    CONSTRAINT uk_lesson_study_day UNIQUE (student_id,module_id,study_date),
    INDEX idx_study_module_date (module_id,study_date,student_id),
    CONSTRAINT fk_study_student FOREIGN KEY (student_id) REFERENCES user_entity(id) ON DELETE CASCADE,
    CONSTRAINT fk_study_module FOREIGN KEY (module_id) REFERENCES ai_learning_modules(id) ON DELETE CASCADE
);
-- History starts at deployment; no invented/backfilled lesson-open history.
