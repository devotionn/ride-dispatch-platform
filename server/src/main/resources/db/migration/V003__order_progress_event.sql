CREATE TABLE order_progress_event (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  driver_id BIGINT NOT NULL,
  stage VARCHAR(40) NOT NULL,
  occurred_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_order_progress_order FOREIGN KEY (order_id) REFERENCES ride_order (id),
  CONSTRAINT fk_order_progress_driver FOREIGN KEY (driver_id) REFERENCES driver (id)
);

CREATE INDEX idx_order_progress_order_time ON order_progress_event (order_id, occurred_at);
CREATE INDEX idx_order_progress_driver_time ON order_progress_event (driver_id, occurred_at);
