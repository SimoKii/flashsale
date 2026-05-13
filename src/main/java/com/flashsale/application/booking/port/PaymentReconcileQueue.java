package com.flashsale.application.booking.port;

import java.time.LocalDateTime;
import java.util.List;

public interface PaymentReconcileQueue {

    void enqueue(
            ReconcileType type,
            Long orderId,
            Long paymentLineId,
            String idempotencyKey
    );

    List<ReconcileItem> findReadyToRetry(int limit);

    void markDone(Long id);

    void markFailed(Long id, String reason);

    void scheduleRetry(Long id, LocalDateTime nextRetryAt);

    enum ReconcileType { PAYMENT_INQUIRY, CANCEL_RETRY }

    record ReconcileItem(
            Long id,
            ReconcileType type,
            Long orderId,
            Long paymentLineId,
            String idempotencyKey,
            int retryCount
    ) {}
}
