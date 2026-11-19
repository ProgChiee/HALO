CREATE TABLE student_registry_import (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 imported_by BIGINT NOT NULL,
 imported_at DATETIME(6) NOT NULL,
 total_rows INT NOT NULL,
 imported_rows INT NOT NULL,
 skipped_rows INT NOT NULL,
 CONSTRAINT fk_registry_import_actor FOREIGN KEY (imported_by) REFERENCES user_entity(id)
);
CREATE TABLE student_registry (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 student_id VARCHAR(32) NOT NULL,
 full_name VARCHAR(100) NOT NULL,
 institutional_email VARCHAR(254) NOT NULL,
 course VARCHAR(16) NOT NULL,
 eligibility_status VARCHAR(16) NOT NULL,
 registration_status VARCHAR(16) NOT NULL,
 linked_user_id BIGINT NULL,
 imported_by BIGINT NOT NULL,
 import_id BIGINT NOT NULL,
 imported_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 verification_hash VARCHAR(128) NULL,
 verification_expires_at DATETIME(6) NULL,
 CONSTRAINT uk_registry_student_id UNIQUE (student_id),
 CONSTRAINT uk_registry_email UNIQUE (institutional_email),
 CONSTRAINT uk_registry_user UNIQUE (linked_user_id),
 CONSTRAINT fk_registry_user FOREIGN KEY (linked_user_id) REFERENCES user_entity(id),
 CONSTRAINT fk_registry_actor FOREIGN KEY (imported_by) REFERENCES user_entity(id),
 CONSTRAINT fk_registry_import FOREIGN KEY (import_id) REFERENCES student_registry_import(id)
);

CREATE INDEX ix_registry_status ON student_registry(eligibility_status, registration_status, id);
