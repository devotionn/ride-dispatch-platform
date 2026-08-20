CREATE TABLE auth_session (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  token_hash VARCHAR(64) NOT NULL,
  principal_type VARCHAR(20) NOT NULL,
  principal_id BIGINT NOT NULL,
  authority VARCHAR(40) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  expires_at TIMESTAMP(6) NOT NULL,
  revoked_at TIMESTAMP(6) NULL,
  CONSTRAINT uk_auth_session_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_auth_session_principal
  ON auth_session (principal_type, principal_id, expires_at);
CREATE INDEX idx_auth_session_expiry
  ON auth_session (expires_at);
