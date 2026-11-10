-- Apply manually only after approval and a verified backup. Existing rows remain unknown.
ALTER TABLE user_entity
    ADD COLUMN has_successful_login BOOLEAN NULL DEFAULT NULL;
