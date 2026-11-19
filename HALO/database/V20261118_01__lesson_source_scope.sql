-- REVIEW ONLY. Never run automatically. Back up and rehearse against a disposable restore first.
-- Nullable preserves all existing published lessons as legacy full-document scope.
ALTER TABLE ai_learning_modules ADD COLUMN source_scope LONGTEXT NULL;
-- Nullable keeps historical messages intact; only matching scope versions enter new scoped prompts.
ALTER TABLE mentor_messages ADD COLUMN source_selection_id VARCHAR(36) NULL;
-- Draft/revision and immutable history payloads store the same versioned sourceScope snapshot.
-- Do not backfill legacy records. No existing content or account rows are updated.
-- Rollback after scoped publication requires restoring compatible application AND source snapshots;
-- dropping this column would destroy approved source provenance and is not a safe rollback.
