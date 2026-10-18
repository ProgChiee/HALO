-- Apply before deploying revision endpoints. No existing material or history is rewritten.
-- One current revision/snapshot per module; published revision metadata is retained until next publication.
CREATE TABLE lesson_revisions (
 module_id BIGINT NOT NULL PRIMARY KEY,
 version BIGINT NOT NULL,
 base_version BIGINT NOT NULL,
 revision_number INT NOT NULL,
 state VARCHAR(16) NOT NULL,
 payload LONGTEXT NOT NULL,
 revised_by BIGINT NOT NULL,
 revised_at DATETIME(6) NOT NULL,
 published_by BIGINT NULL,
 published_at DATETIME(6) NULL,
 CONSTRAINT fk_lesson_revision_module FOREIGN KEY (module_id) REFERENCES ai_learning_modules(id)
);
-- Actor IDs are audit snapshots, not cascading account relationships.
