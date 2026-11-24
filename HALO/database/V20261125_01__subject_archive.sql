-- Apply manually after reviewing the target schema. Existing subjects remain active.
ALTER TABLE subjects ADD COLUMN archived_at DATETIME(6) NULL;
