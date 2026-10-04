-- MariaDB/MySQL. Run preflight first; stop if any rows are returned.
SELECT subject_id, week_number, COUNT(*) AS duplicate_count,
       GROUP_CONCAT(id ORDER BY id) AS week_ids
FROM weeks GROUP BY subject_id, week_number HAVING COUNT(*) > 1;
SHOW INDEX FROM weeks;
-- Apply only if preflight is empty and no equivalent unique index exists.
-- DDL implicitly commits. Back up first and pause academic writes during deployment.
-- No DELETE/UPDATE: if duplicates race preflight, ALTER fails without removing rows.
ALTER TABLE weeks ADD CONSTRAINT uk_week_subject_number UNIQUE (subject_id, week_number);
