-- MariaDB 10.4 / MySQL 8: inspect before applying; do not rewrite existing emails.
-- Check equivalent unique indexes in information_schema.STATISTICS first.
-- Preflight (must return no rows):
SELECT LOWER(TRIM(email)) AS normalized_email, COUNT(*) AS duplicate_count
FROM user_entity GROUP BY LOWER(TRIM(email)) HAVING COUNT(*) > 1;
-- Run the ALTER only when the preflight is clear and this index does not already exist.
-- One atomic ALTER: duplicate concurrent writes cause the operation to fail safely.
ALTER TABLE user_entity
    ADD COLUMN email_normalized VARCHAR(255) GENERATED ALWAYS AS (LOWER(TRIM(email))) VIRTUAL,
    ADD CONSTRAINT uk_user_email_normalized UNIQUE (email_normalized);
