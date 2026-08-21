ALTER TABLE ride_order ADD COLUMN idempotency_key VARCHAR(80) NULL;
ALTER TABLE ride_order ADD COLUMN request_fingerprint VARCHAR(64) NULL;
ALTER TABLE ride_order ADD CONSTRAINT uk_ride_order_idempotency_key UNIQUE (idempotency_key);

CREATE TABLE passenger_order_access_token (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  token_hash VARCHAR(64) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT uk_passenger_order_access_token_hash UNIQUE (token_hash),
  CONSTRAINT fk_passenger_order_access_token_order FOREIGN KEY (order_id) REFERENCES ride_order (id)
);

CREATE INDEX idx_passenger_order_access_token_order ON passenger_order_access_token (order_id, created_at);
