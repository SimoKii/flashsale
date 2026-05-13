ALTER TABLE point_tx
    ADD COLUMN idempotency_key VARCHAR(255) NULL AFTER type,
    ADD UNIQUE KEY uq_point_tx_idempotency_key (idempotency_key);
