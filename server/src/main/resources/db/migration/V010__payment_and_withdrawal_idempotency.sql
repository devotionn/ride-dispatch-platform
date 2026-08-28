ALTER TABLE payment_attempt ADD COLUMN idempotency_key VARCHAR(120) NULL;
ALTER TABLE payment_attempt ADD CONSTRAINT uk_payment_attempt_idempotency UNIQUE (idempotency_key);

ALTER TABLE withdrawal ADD COLUMN idempotency_key VARCHAR(120) NULL;
ALTER TABLE withdrawal ADD CONSTRAINT uk_withdrawal_idempotency UNIQUE (idempotency_key);
