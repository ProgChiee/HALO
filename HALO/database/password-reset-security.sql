-- Apply ONCE, with every HALO backend instance stopped. MariaDB/MySQL DDL implicitly commits.
-- No user passwords, token versions, account states, or audit history are changed.
ALTER TABLE password_reset_otps
    ADD COLUMN failed_attempts INT NOT NULL DEFAULT 0;

CREATE TABLE password_reset_rate_limits (
    bucket_key VARCHAR(64) NOT NULL PRIMARY KEY,
    window_started_at DATETIME(6) NOT NULL,
    event_count INT NOT NULL,
    next_allowed_at DATETIME(6) NULL,
    INDEX idx_reset_limit_window (window_started_at)
) ENGINE=InnoDB;

-- Outstanding plaintext challenges cannot be safely carried forward. Users request a new code.
-- Keep the rows, but erase the plaintext and expire only legacy challenges.
UPDATE password_reset_otps
SET otp = '', expires_at = CURRENT_TIMESTAMP(6), failed_attempts = 0
WHERE otp NOT LIKE 'v1:%';
