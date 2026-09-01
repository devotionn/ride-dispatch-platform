CREATE TABLE passenger_complaint (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  complaint_no VARCHAR(40) NOT NULL,
  order_id BIGINT NULL,
  order_no VARCHAR(40) NULL,
  category VARCHAR(40) NOT NULL,
  description VARCHAR(1000) NOT NULL,
  contact_mobile VARCHAR(30) NULL,
  status VARCHAR(30) NOT NULL,
  handle_note VARCHAR(500) NULL,
  handled_by BIGINT NULL,
  handled_at TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT uk_passenger_complaint_no UNIQUE (complaint_no),
  CONSTRAINT fk_passenger_complaint_order FOREIGN KEY (order_id) REFERENCES ride_order (id)
);

CREATE INDEX idx_passenger_complaint_status ON passenger_complaint (status, created_at);
CREATE INDEX idx_passenger_complaint_order ON passenger_complaint (order_id);

CREATE TABLE safety_alarm (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NULL,
  order_no VARCHAR(40) NULL,
  source_page VARCHAR(40) NOT NULL,
  latitude DECIMAL(10,7) NULL,
  longitude DECIMAL(10,7) NULL,
  location_text VARCHAR(255) NULL,
  passenger_mobile VARCHAR(30) NULL,
  created_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_safety_alarm_order FOREIGN KEY (order_id) REFERENCES ride_order (id)
);

CREATE INDEX idx_safety_alarm_created ON safety_alarm (created_at);
CREATE INDEX idx_safety_alarm_order ON safety_alarm (order_id);
