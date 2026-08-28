CREATE TABLE payment_exception (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  exception_no VARCHAR(40) NOT NULL,
  payment_id BIGINT NOT NULL,
  order_id BIGINT NOT NULL,
  requested_amount BIGINT NOT NULL,
  reason VARCHAR(500) NOT NULL,
  status VARCHAR(30) NOT NULL,
  external_refund_ref VARCHAR(120) NULL,
  resolution_note VARCHAR(500) NULL,
  created_by BIGINT NOT NULL,
  resolved_by BIGINT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  resolved_at TIMESTAMP(6) NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uk_payment_exception_no UNIQUE (exception_no),
  CONSTRAINT fk_payment_exception_payment FOREIGN KEY (payment_id) REFERENCES payment (id),
  CONSTRAINT fk_payment_exception_order FOREIGN KEY (order_id) REFERENCES ride_order (id)
);

CREATE INDEX idx_payment_exception_status_created ON payment_exception (status, created_at);
CREATE INDEX idx_payment_exception_payment ON payment_exception (payment_id, created_at);
