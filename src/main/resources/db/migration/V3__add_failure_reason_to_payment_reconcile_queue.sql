ALTER TABLE payment_reconcile_queue
    ADD COLUMN failure_reason TEXT NULL AFTER retry_count;
