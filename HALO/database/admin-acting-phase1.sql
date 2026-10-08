-- Manual migration: MySQL 8 / MariaDB 10.4. Select the HALO database first.
-- Stop application writers. Back up schema/data. Do not run the application with ddl-auto=update
-- before applying this migration. Use SPRING_JPA_HIBERNATE_DDL_AUTO=validate for controlled deployment.
-- This is additive: no existing audit/user rows are rewritten or removed.
-- Inspect first. If any proposed table/columns already exist, stop and reconcile metadata;
-- do not blindly rerun partially applied DDL (MySQL DDL auto-commits).
USE halo_db;
SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'admin_acting_session';
SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'activity_log_entity' AND COLUMN_NAME IN ('acting_target_id','acting_target_role','acting_session_id');

DELIMITER $$
CREATE PROCEDURE add_admin_acting_phase1()
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admin_acting_session')
       OR EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'activity_log_entity' AND COLUMN_NAME IN ('acting_target_id','acting_target_role','acting_session_id')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Acting migration blocked: existing or partial schema requires review';
    END IF;
    CREATE TABLE admin_acting_session (
        id VARCHAR(36) NOT NULL PRIMARY KEY,
        admin_id BIGINT NOT NULL,
        target_id BIGINT NOT NULL,
        target_role VARCHAR(32) NOT NULL,
        admin_token_version BIGINT NOT NULL,
        target_token_version BIGINT NOT NULL,
        created_at DATETIME(6) NOT NULL,
        expires_at DATETIME(6) NOT NULL,
        revoked_at DATETIME(6) NULL,
        INDEX idx_acting_admin_expiry (admin_id, expires_at),
        CONSTRAINT fk_acting_admin FOREIGN KEY (admin_id) REFERENCES user_entity(id),
        CONSTRAINT fk_acting_target FOREIGN KEY (target_id) REFERENCES user_entity(id)
    );
    ALTER TABLE activity_log_entity
        ADD COLUMN acting_target_id BIGINT NULL,
        ADD COLUMN acting_target_role VARCHAR(32) NULL,
        ADD COLUMN acting_session_id VARCHAR(36) NULL,
        ADD CONSTRAINT fk_activity_acting_target FOREIGN KEY (acting_target_id) REFERENCES user_entity(id),
        ADD CONSTRAINT fk_activity_acting_session FOREIGN KEY (acting_session_id) REFERENCES admin_acting_session(id);
END$$
DELIMITER ;
CALL add_admin_acting_phase1();
DROP PROCEDURE add_admin_acting_phase1;
-- On failure, inspect partial DDL before removing/recreating only the helper procedure.
