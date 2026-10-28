-- MANUAL ONLY: backup halo_db and obtain deployment approval before execution.
-- Verified repository mapping: user_entity.id and subjects.id (signed BIGINT).
-- Inspect SHOW CREATE TABLE for both parents in the deployed schema first.
-- No backfill, UPDATE, DELETE, or changes to existing learning tables.
CREATE TABLE student_subject_enrollments (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 student_id BIGINT NOT NULL,
 subject_id BIGINT NOT NULL,
 recorded_at_epoch_ms BIGINT NOT NULL,
 source VARCHAR(32) NOT NULL,
 CONSTRAINT uk_student_subject_enrollment UNIQUE(student_id, subject_id),
 CONSTRAINT fk_enrollment_student FOREIGN KEY(student_id) REFERENCES user_entity(id),
 CONSTRAINT fk_enrollment_subject FOREIGN KEY(subject_id) REFERENCES subjects(id)
) ENGINE=InnoDB;
-- Verification (read only):
SHOW CREATE TABLE student_subject_enrollments;
SELECT COUNT(*) AS enrollment_count FROM student_subject_enrollments;
