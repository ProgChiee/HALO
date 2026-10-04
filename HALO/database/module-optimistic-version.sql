-- Stop all backend instances before applying. Inspect SHOW COLUMNS first; skip columns already present.
-- Additive migration: preserves existing module data and initializes old versions.
ALTER TABLE ai_learning_modules ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE ai_learning_modules ADD COLUMN generation_token VARCHAR(36) NULL;
-- Restart all instances with version-aware code together. Do not mix old and new writers.
