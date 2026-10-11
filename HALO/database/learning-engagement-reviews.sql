-- Manual migration, MySQL 8 / MariaDB 10.4. NOT applied automatically.
-- Requires learning-engagement-study-days.sql first. Stop writers; use ddl-auto=validate.
-- Inspect first. Stop if either column already exists; do not rerun partial DDL.
SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
 AND TABLE_NAME='lesson_study_days' AND COLUMN_NAME IN ('last_opened_at','review_count');
ALTER TABLE lesson_study_days
 ADD COLUMN last_opened_at DATETIME(6) NULL,
 ADD COLUMN review_count BIGINT NOT NULL DEFAULT 0;
-- Existing study dates remain intact. Historical revisit counts are unknown and
-- are not invented. New review counts begin at deployment. The first observed
-- open on a legacy same-day row establishes its timestamp, without adding a review.
c