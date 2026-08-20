CREATE TABLE platform_brand (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  company_name VARCHAR(120) NOT NULL,
  logo_url VARCHAR(500) NULL,
  updated_by BIGINT NULL,
  updated_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE admin_user (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(80) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(80) NOT NULL,
  role VARCHAR(30) NOT NULL,
  status VARCHAR(30) NOT NULL,
  last_login_at TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT uk_admin_user_username UNIQUE (username)
);

CREATE TABLE driver (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  driver_no VARCHAR(50) NOT NULL,
  name VARCHAR(80) NOT NULL,
  mobile VARCHAR(30) NOT NULL,
  password_hash VARCHAR(255) NULL,
  account_status VARCHAR(30) NOT NULL,
  work_status VARCHAR(30) NOT NULL,
  max_passengers INT NOT NULL,
  available_passengers INT NOT NULL,
  qr_short_code VARCHAR(32) NOT NULL,
  default_vehicle_id BIGINT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uk_driver_no UNIQUE (driver_no),
  CONSTRAINT uk_driver_qr_short_code UNIQUE (qr_short_code)
);

CREATE INDEX idx_driver_mobile ON driver (mobile);
CREATE INDEX idx_driver_availability ON driver (account_status, work_status, available_passengers);

CREATE TABLE vehicle (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  driver_id BIGINT NOT NULL,
  plate_no VARCHAR(32) NOT NULL,
  brand_model VARCHAR(120) NULL,
  max_passengers INT NOT NULL,
  status VARCHAR(30) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT uk_vehicle_plate_no UNIQUE (plate_no),
  CONSTRAINT fk_vehicle_driver FOREIGN KEY (driver_id) REFERENCES driver (id)
);

CREATE INDEX idx_vehicle_driver ON vehicle (driver_id);

CREATE TABLE driver_location_current (
  driver_id BIGINT PRIMARY KEY,
  latitude DECIMAL(10, 7) NOT NULL,
  longitude DECIMAL(10, 7) NOT NULL,
  accuracy_meters DECIMAL(10, 2) NULL,
  source VARCHAR(30) NOT NULL,
  located_at TIMESTAMP(6) NOT NULL,
  received_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_driver_location_driver FOREIGN KEY (driver_id) REFERENCES driver (id)
);

CREATE TABLE ride_order (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_no VARCHAR(40) NOT NULL,
  source_type VARCHAR(30) NOT NULL,
  source_driver_id BIGINT NULL,
  current_driver_id BIGINT NULL,
  passenger_mobile VARCHAR(30) NOT NULL,
  passenger_access_token_hash VARCHAR(64) NOT NULL,
  pickup_address VARCHAR(255) NOT NULL,
  pickup_latitude DECIMAL(10, 7) NOT NULL,
  pickup_longitude DECIMAL(10, 7) NOT NULL,
  destination_address VARCHAR(255) NOT NULL,
  destination_latitude DECIMAL(10, 7) NOT NULL,
  destination_longitude DECIMAL(10, 7) NOT NULL,
  passenger_count INT NOT NULL,
  departure_at TIMESTAMP(6) NOT NULL,
  remark VARCHAR(500) NULL,
  status VARCHAR(40) NOT NULL,
  trip_stage VARCHAR(40) NULL,
  final_amount BIGINT NULL,
  settlement_method VARCHAR(30) NULL,
  accepted_at TIMESTAMP(6) NULL,
  service_started_at TIMESTAMP(6) NULL,
  arrived_destination_at TIMESTAMP(6) NULL,
  completed_at TIMESTAMP(6) NULL,
  cancelled_at TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uk_ride_order_no UNIQUE (order_no),
  CONSTRAINT fk_order_source_driver FOREIGN KEY (source_driver_id) REFERENCES driver (id),
  CONSTRAINT fk_order_current_driver FOREIGN KEY (current_driver_id) REFERENCES driver (id)
);

CREATE INDEX idx_order_status_departure ON ride_order (status, departure_at);
CREATE INDEX idx_order_current_driver_status ON ride_order (current_driver_id, status);
CREATE INDEX idx_order_source_driver_created ON ride_order (source_driver_id, created_at);
CREATE INDEX idx_order_created ON ride_order (created_at);

CREATE TABLE dispatch_attempt (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  target_driver_id BIGINT NOT NULL,
  dispatch_type VARCHAR(40) NOT NULL,
  status VARCHAR(40) NOT NULL,
  dispatched_by BIGINT NULL,
  dispatched_at TIMESTAMP(6) NOT NULL,
  responded_at TIMESTAMP(6) NULL,
  reject_reason_code VARCHAR(60) NULL,
  reject_reason_text VARCHAR(255) NULL,
  reassign_from_driver_id BIGINT NULL,
  reassign_reason VARCHAR(255) NULL,
  invalidated_at TIMESTAMP(6) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT fk_dispatch_order FOREIGN KEY (order_id) REFERENCES ride_order (id),
  CONSTRAINT fk_dispatch_target_driver FOREIGN KEY (target_driver_id) REFERENCES driver (id)
);

CREATE INDEX idx_dispatch_order_time ON dispatch_attempt (order_id, dispatched_at);
CREATE INDEX idx_dispatch_driver_status ON dispatch_attempt (target_driver_id, status, dispatched_at);
CREATE INDEX idx_dispatch_status_time ON dispatch_attempt (status, dispatched_at);

CREATE TABLE operation_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  operator_type VARCHAR(30) NOT NULL,
  operator_id BIGINT NULL,
  object_type VARCHAR(50) NOT NULL,
  object_id VARCHAR(80) NOT NULL,
  action VARCHAR(80) NOT NULL,
  before_json TEXT NULL,
  after_json TEXT NULL,
  reason VARCHAR(500) NULL,
  request_id VARCHAR(80) NULL,
  created_at TIMESTAMP(6) NOT NULL
);

CREATE INDEX idx_operation_object ON operation_log (object_type, object_id, created_at);
CREATE INDEX idx_operation_operator ON operation_log (operator_id, created_at);
CREATE INDEX idx_operation_action ON operation_log (action, created_at);
