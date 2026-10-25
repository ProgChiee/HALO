-- APPROVAL REQUIRED before running against a live database.
-- See lesson-revision-history.md. Existing tables and payloads are never rewritten.
-- MariaDB DDL commits implicitly; take a database + uploaded-material backup first.
CREATE TABLE lesson_revision_history (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 module_id BIGINT NOT NULL,
 revision_number INT NOT NULL,
 snapshot_kind VARCHAR(24) NOT NULL,
 revision_version BIGINT NULL,
 base_version BIGINT NULL,
 published_module_version BIGINT NULL,
 payload LONGTEXT NOT NULL,
 revised_by BIGINT NULL,
 revised_at DATETIME(6) NULL,
 published_by BIGINT NULL,
 published_at DATETIME(6) NULL,
 recorded_at DATETIME(6) NOT NULL,
 CONSTRAINT uq_lesson_history_version UNIQUE(module_id,revision_number),
 CONSTRAINT fk_lesson_history_module FOREIGN KEY(module_id) REFERENCES ai_learning_modules(id)
);
-- No cascading deletion, and actor IDs remain historical values rather than user FKs.
-- Copy only intact published snapshots. DRAFT/REVIEWED/DISCARDED are NOT previous history.
START TRANSACTION;
INSERT INTO lesson_revision_history
 (module_id,revision_number,snapshot_kind,revision_version,base_version,payload,
  revised_by,revised_at,published_by,published_at,recorded_at)
SELECT r.module_id,r.revision_number,'REVISION',r.version,r.base_version,r.payload,
 r.revised_by,r.revised_at,r.published_by,r.published_at,CURRENT_TIMESTAMP(6)
FROM lesson_revisions r
WHERE r.state='PUBLISHED'
 AND NOT EXISTS (SELECT 1 FROM lesson_revision_history h
                 WHERE h.module_id=r.module_id AND h.revision_number=r.revision_number);
COMMIT;
-- Verification: every extant PUBLISHED working row must have a byte-identical copy.
SELECT r.module_id,r.revision_number
FROM lesson_revisions r LEFT JOIN lesson_revision_history h
 ON h.module_id=r.module_id AND h.revision_number=r.revision_number
WHERE r.state='PUBLISHED' AND (h.id IS NULL OR BINARY h.payload<>BINARY r.payload);
