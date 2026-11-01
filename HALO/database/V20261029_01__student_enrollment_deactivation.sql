-- MANUAL ONLY. Live execution requires separate approval and a verified backup.
-- Apply once, with HALO stopped, after student-subject-enrollment.sql.
-- Preserve all existing rows, keys, source values and original enrollment timestamps.
ALTER TABLE student_subject_enrollments ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE student_subject_enrollments ADD COLUMN unenrolled_at_epoch_ms BIGINT NULL;
