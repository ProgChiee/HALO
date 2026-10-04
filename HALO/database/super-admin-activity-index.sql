-- No equivalent ordering index existed in the inspected local database.
-- Apply once on other deployments after checking information_schema.STATISTICS.
CREATE INDEX idx_activity_created_id ON activity_log_entity (created_at, id);
