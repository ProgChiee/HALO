-- Run with the application stopped, after taking a backup, against the HALO database.
-- Does not rewrite, delete, or relabel earned badges.
DELIMITER $$
CREATE PROCEDURE st09_badge_catalog_migration()
BEGIN
    DECLARE current_type TEXT;
    IF EXISTS (SELECT 1 FROM student_badges WHERE badge_type = 'HALFWAY_THERE') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Legacy HALFWAY_THERE rows exist. Review manually before deploying ST-09; no rows changed.';
    END IF;
    SELECT COLUMN_TYPE INTO current_type FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'student_badges' AND COLUMN_NAME = 'badge_type';
    IF current_type IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'student_badges.badge_type column not found';
    END IF;
    IF current_type LIKE 'enum(%' AND LOCATE('''MODULE_FINISHER''', current_type) = 0 THEN
        IF LOCATE('''HALFWAY_THERE''', current_type) > 0 THEN
            SET current_type = REPLACE(current_type, '''HALFWAY_THERE''', '''MODULE_FINISHER''');
        ELSE
            SET current_type = CONCAT(LEFT(current_type, LENGTH(current_type)-1), ',''MODULE_FINISHER'')');
        END IF;
        SET @st09_ddl = CONCAT('ALTER TABLE student_badges MODIFY COLUMN badge_type ', current_type, ' NOT NULL');
        PREPARE st09_stmt FROM @st09_ddl;
        EXECUTE st09_stmt;
        DEALLOCATE PREPARE st09_stmt;
    END IF;
END$$
DELIMITER ;
CALL st09_badge_catalog_migration();
DROP PROCEDURE st09_badge_catalog_migration;
