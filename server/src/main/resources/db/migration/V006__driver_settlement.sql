CREATE TABLE driver_account (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  driver_id BIGINT NOT NULL,
  available_balance BIGINT NOT NULL DEFAULT 0,
  frozen_balance BIGINT NOT NULL DEFAULT 0,
  business_income_total BIGINT NOT NULL DEFAULT 0,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT uk_driver_account_driver UNIQUE (driver_id),
  CONSTRAINT fk_driver_account_driver FOREIGN KEY (driver_id) REFERENCES driver (id)
);

CREATE TABLE withdrawal (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  withdrawal_no VARCHAR(40) NOT NULL,
  driver_id BIGINT NOT NULL,
  amount BIGINT NOT NULL,
  channel VARCHAR(30) NOT NULL,
  account VARCHAR(255) NOT NULL,
  status VARCHAR(40) NOT NULL,
  reason VARCHAR(500) NULL,
  reviewed_by BIGINT NULL,
  reviewed_at TIMESTAMP(6) NULL,
  paid_by BIGINT NULL,
  planned_paid_at TIMESTAMP(6) NULL,
  paid_at TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uk_withdrawal_no UNIQUE (withdrawal_no),
  CONSTRAINT fk_withdrawal_driver FOREIGN KEY (driver_id) REFERENCES driver (id)
);

CREATE INDEX idx_withdrawal_driver_status ON withdrawal (driver_id, status, created_at);
CREATE INDEX idx_withdrawal_status_created ON withdrawal (status, created_at);

CREATE TABLE driver_ledger (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  ledger_no VARCHAR(40) NOT NULL,
  driver_id BIGINT NOT NULL,
  order_id BIGINT NULL,
  withdrawal_id BIGINT NULL,
  ledger_type VARCHAR(40) NOT NULL,
  amount BIGINT NOT NULL,
  available_before BIGINT NOT NULL,
  available_after BIGINT NOT NULL,
  frozen_before BIGINT NOT NULL,
  frozen_after BIGINT NOT NULL,
  business_income_amount BIGINT NOT NULL DEFAULT 0,
  withdrawable_delta BIGINT NOT NULL DEFAULT 0,
  event_key VARCHAR(120) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT uk_driver_ledger_no UNIQUE (ledger_no),
  CONSTRAINT uk_driver_ledger_event_key UNIQUE (event_key),
  CONSTRAINT fk_driver_ledger_driver FOREIGN KEY (driver_id) REFERENCES driver (id),
  CONSTRAINT fk_driver_ledger_order FOREIGN KEY (order_id) REFERENCES ride_order (id),
  CONSTRAINT fk_driver_ledger_withdrawal FOREIGN KEY (withdrawal_id) REFERENCES withdrawal (id)
);

CREATE INDEX idx_driver_ledger_driver_created ON driver_ledger (driver_id, created_at);
CREATE INDEX idx_driver_ledger_order ON driver_ledger (order_id, created_at);
