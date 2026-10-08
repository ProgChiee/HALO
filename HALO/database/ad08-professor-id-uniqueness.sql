-- MySQL 8 / MariaDB 10.4. Select halo_db before running with a client supporting DELIMITER.
-- Stop application writers while applying. No existing IDs are rewritten or deleted.
-- Case is preserved: PROF-1 and prof-1 remain different identifiers.
-- Inspect deployed metadata and duplicate groups first:
SELECT INDEX_NAME, NON_UNIQUE, COLUMN_NAME FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'professor_entity';
SELECT HEX(CONVERT(TRIM(professor_id) USING binary)) AS normalized_id_hex, COUNT(*) AS duplicate_count
FROM professor_entity WHERE professor_id IS NOT NULL
GROUP BY CONVERT(TRIM(professor_id) USING binary) HAVING COUNT(*) > 1;

DELIMITER $$
CREATE PROCEDURE ad08_add_professor_id_uniqueness()
BEGIN
    IF EXISTS (SELECT 1 FROM professor_entity WHERE professor_id IS NOT NULL
               GROUP BY CONVERT(TRIM(professor_id) USING binary) HAVING COUNT(*) > 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AD08 blocked: duplicate normalized Professor IDs require manual review';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'professor_entity' AND COLUMN_NAME = 'professor_id_normalized')
       OR EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'professor_entity' AND INDEX_NAME = 'uk_professor_id_normalized') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'AD08 already installed or conflicting metadata exists: inspect before proceeding';
    END IF;
    -- Binary generated key makes uniqueness independent of the table's case-insensitive collation.
    -- This also catches direct SQL writes with surrounding spaces. Original display values stay intact.
    -- A concurrent duplicate causes this ALTER to fail rather than deleting or merging any rows.
    ALTER TABLE professor_entity
        ADD COLUMN professor_id_normalized VARBINARY(1020)
            GENERATED ALWAYS AS (CONVERT(TRIM(professor_id) USING binary)) VIRTUAL,
        ADD CONSTRAINT uk_professor_id_normalized UNIQUE (professor_id_normalized);
END$$
DELIMITER ;
CALL ad08_add_professor_id_uniqueness();
DROP PROCEDURE ad08_add_professor_id_uniqueness;
-- If the CALL fails and the client stops, remove only the helper procedure after reviewing the failure.
-- The generated column is deliberately not JPA-mapped: deploy this migration explicitly.
