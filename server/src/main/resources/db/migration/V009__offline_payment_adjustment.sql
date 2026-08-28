CREATE TABLE offline_payment_adjustment (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  adjustment_no VARCHAR(40) NOT NULL,
  payment_id BIGINT NOT NULL,
  order_id BIGINT NOT NULL,
  driver_id BIGINT NOT NULL,
  delta_amount BIGINT NOT NULL,
  reason VARCHAR(500) NOT NULL,
  idempotency_key VARCHAR(120) NOT NULL,
  created_by BIGINT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT uk_offline_adjustment_no UNIQUE (adjustment_no),
  CONSTRAINT uk_offline_adjustment_idempotency UNIQUE (idempotency_key),
  CONSTRAINT fk_offline_adjustment_payment FOREIGN KEY (payment_id) REFERENCES payment (id),
  CONSTRAINT fk_offline_adjustment_order FOREIGN KEY (order_id) REFERENCES ride_order (id),
  CONSTRAINT fk_offline_adjustment_driver FOREIGN KEY (driver_id) REFERENCES driver (id)
);

CREATE INDEX idx_offline_adjustment_payment_created ON offline_payment_adjustment (payment_id, created_at);
