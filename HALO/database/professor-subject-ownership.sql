-- Apply once, before starting the updated backend. No ownership backfill is performed.
-- Inspect first; if Hibernate has already created equivalent objects, do not add them twice.
SHOW COLUMNS FROM subjects LIKE 'professor_id';
SHOW CREATE TABLE subjects;

ALTER TABLE subjects
    ADD COLUMN professor_id BIGINT NULL,
    ADD INDEX idx_subject_professor (professor_id),
    ADD CONSTRAINT fk_subject_professor FOREIGN KEY (professor_id)
        REFERENCES professor_entity(id);

-- Keep NULL for legacy records until an administrator approves each mapping.
-- professor_id references professor_entity.id, NOT its string professor_id or user_id.
