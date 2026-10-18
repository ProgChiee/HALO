-- Manual migration. Not applied by this change. Back up the database and stop writers first.
-- Select the HALO database. Inspect metadata before applying; do not rerun partial DDL.
-- Existing rows are neither rewritten nor deleted. Existing foreign keys are retained.
-- Required for unknown-account login failures and durable logout deduplication.
USE halo_db;
ALTER TABLE activity_log_entity
 MODIFY COLUMN user_id BIGINT NULL,
 ADD COLUMN auth_event VARCHAR(64) NULL,
 ADD COLUMN auth_display VARCHAR(512) NULL,
 ADD COLUMN auth_event_key VARCHAR(64) NULL,
 ADD UNIQUE KEY uk_activity_auth_event_key (auth_event_key);
