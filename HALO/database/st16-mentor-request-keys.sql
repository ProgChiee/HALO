-- Stop application writers and back up the database before applying.
-- Existing messages keep NULL request IDs; no historical content is rewritten.
DELIMITER $$
CREATE PROCEDURE st16_mentor_request_keys()
BEGIN
 IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mentor_messages' AND COLUMN_NAME='request_id') THEN
  ALTER TABLE mentor_messages ADD COLUMN request_id VARCHAR(36) NULL;
 END IF;
 IF EXISTS (SELECT 1 FROM mentor_messages WHERE request_id IS NOT NULL GROUP BY session_id,request_id,sender HAVING COUNT(*)>1) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Duplicate Mentor request keys exist; review manually. No messages deleted.';
 END IF;
 IF NOT EXISTS (SELECT 1 FROM (SELECT INDEX_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='mentor_messages' AND NON_UNIQUE=0 GROUP BY INDEX_NAME HAVING GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)='session_id,request_id,sender') indexes_found) THEN
  ALTER TABLE mentor_messages ADD CONSTRAINT uk_mentor_request_sender UNIQUE(session_id,request_id,sender);
 END IF;
END$$
DELIMITER ;
CALL st16_mentor_request_keys();
DROP PROCEDURE st16_mentor_request_keys;
