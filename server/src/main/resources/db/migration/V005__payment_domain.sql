CREATE TABLE payment (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  payment_no VARCHAR(40) NOT NULL,
  order_id BIGINT NOT NULL,
  amount BIGINT NOT NULL,
  status VARCHAR(30) NOT NULL,
  settlement_method VARCHAR(30) NULL,
  access_token_hash VARCHAR(64) NOT NULL,
  access_token_created_at TIMESTAMP(6) NOT NULL,
  expires_at TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) NOT NULL,
  settled_at TIMESTAMP(6) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uk_payment_no UNIQUE (payment_no),
  CONSTRAINT uk_payment_order UNIQUE (order_id),
  CONSTRAINT uk_payment_access_token_hash UNIQUE (access_token_hash),
  CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES ride_order (id)
);

CREATE INDEX idx_payment_status_created ON payment (status, created_at);

CREATE TABLE payment_attempt (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  attempt_no VARCHAR(40) NOT NULL,
  payment_id BIGINT NOT NULL,
  channel VARCHAR(30) NOT NULL,
  merchant_order_no VARCHAR(80) NOT NULL,
  third_party_transaction_no VARCHAR(100) NULL,
  amount BIGINT NOT NULL,
  status VARCHAR(30) NOT NULL,
  callback_payload_digest VARCHAR(64) NULL,
  created_at TIMESTAMP(6) NOT NULL,
  paid_at TIMESTAMP(6) NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uk_payment_attempt_no UNIQUE (attempt_no),
  CONSTRAINT uk_payment_attempt_merchant_order_no UNIQUE (merchant_order_no),
  CONSTRAINT uk_payment_attempt_third_party_tx UNIQUE (third_party_transaction_no),
  CONSTRAINT fk_payment_attempt_payment FOREIGN KEY (payment_id) REFERENCES payment (id)
);

CREATE INDEX idx_payment_attempt_payment_created ON payment_attempt (payment_id, created_at);
CREATE INDEX idx_payment_attempt_status_created ON payment_attempt (status, created_at);
