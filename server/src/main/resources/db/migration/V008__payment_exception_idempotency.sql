ALTER TABLE payment_exception ADD COLUMN idempotency_key VARCHAR(80) NULL;
ALTER TABLE payment_exception ADD CONSTRAINT uk_payment_exception_idempotency_key UNIQUE (idempotency_key);
