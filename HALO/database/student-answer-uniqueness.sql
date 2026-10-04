-- MariaDB/MySQL: select the intended database first. Pause all submission writers.
-- Inspect before executing the guarded DDL below. Never remove duplicates automatically.
SELECT attempt_id, question_id, COUNT(*) AS duplicate_count
FROM student_answers GROUP BY attempt_id, question_id HAVING COUNT(*) > 1;
SELECT INDEX_NAME, NON_UNIQUE, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS columns_used
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'student_answers'
GROUP BY INDEX_NAME, NON_UNIQUE;

-- Abort rather than change any records when duplicates exist. Skip equivalent indexes.
DELIMITER //
CREATE PROCEDURE migrate_student_answer_uniqueness()
BEGIN
    IF EXISTS (SELECT 1 FROM student_answers GROUP BY attempt_id, question_id HAVING COUNT(*) > 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Duplicate answer pairs exist; review records before migration';
    ELSEIF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'student_answers' AND NON_UNIQUE = 0
        GROUP BY INDEX_NAME HAVING COUNT(*) = 2
        AND SUM(COLUMN_NAME IN ('attempt_id', 'question_id')) = 2
    ) THEN
        ALTER TABLE student_answers ADD CONSTRAINT uk_student_answer_attempt_question UNIQUE (attempt_id, question_id);
    END IF;
END//
DELIMITER ;
CALL migrate_student_answer_uniqueness();
DROP PROCEDURE migrate_student_answer_uniqueness;
