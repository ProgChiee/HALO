-- Manual migration, MySQL/MariaDB. Stop application writers and back up first.
-- Apply before deploying the updated application. No account/history rows are removed.
USE halo_db;
ALTER TABLE user_entity
  MODIFY COLUMN status ENUM('ACTIVE','BLOCKED','INACTIVE','ARCHIVED') NULL,
  ADD COLUMN previous_status_before_archive VARCHAR(16) NULL;
-- Existing statuses are unchanged. ARCHIVED is set only by the explicit archive action.
