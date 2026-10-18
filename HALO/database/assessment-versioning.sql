-- Apply once before deploying quiz revisions. Not automatically applied.
-- Requires lesson-material-revisions.sql. No attempts/answers/scores are modified.
ALTER TABLE ai_learning_modules ADD COLUMN assessment_needs_review BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE assessments
 ADD COLUMN quiz_version INT NOT NULL DEFAULT 1,
 ADD COLUMN source_module_version BIGINT NULL,
 ADD COLUMN approved_at DATETIME(6) NULL,
 MODIFY COLUMN status VARCHAR(20) NOT NULL;
CREATE INDEX ix_assessment_module_history ON assessments(module_id);
SET @old_module_unique = (SELECT INDEX_NAME FROM information_schema.STATISTICS
 WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='assessments' AND NON_UNIQUE=0 AND INDEX_NAME<>'PRIMARY'
 GROUP BY INDEX_NAME HAVING COUNT(*)=1 AND MAX(COLUMN_NAME)='module_id' LIMIT 1);
SET @drop_sql = IF(@old_module_unique IS NULL,'SELECT 1',CONCAT('ALTER TABLE assessments DROP INDEX `',REPLACE(@old_module_unique,'`','``'),'`'));
PREPARE drop_old_quiz_index FROM @drop_sql;
EXECUTE drop_old_quiz_index;
DEALLOCATE PREPARE drop_old_quiz_index;
ALTER TABLE assessments ADD CONSTRAINT uk_assessment_module_version UNIQUE(module_id,quiz_version);
ALTER TABLE assessments ADD COLUMN current_module_id BIGINT GENERATED ALWAYS AS
 (CASE WHEN status IN ('AVAILABLE','INACTIVE') THEN module_id ELSE NULL END) STORED,
 ADD CONSTRAINT uk_assessment_current_module UNIQUE(current_module_id);
