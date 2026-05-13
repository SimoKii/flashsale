package com.flashsale.infrastructure.jpa.reconcile;

import com.flashsale.application.booking.port.PaymentReconcileQueue.ReconcileItem;
import com.flashsale.application.booking.port.PaymentReconcileQueue.ReconcileType;
import com.flashsale.infrastructure.jpa.base.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_reconcile_queue")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentReconcileQueueJpaEntity extends BaseJpaEntity {

    public enum Status { PENDING, DONE, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReconcileType type;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "payment_line_id")
    private Long paymentLineId;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_retry_at", nullable = false)
    private LocalDateTime nextRetryAt;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    public static PaymentReconcileQueueJpaEntity create(
            final ReconcileType type,
            final Long orderId,
            final Long paymentLineId,
            final String idempotencyKey
    ) {
        PaymentReconcileQueueJpaEntity entity = new PaymentReconcileQueueJpaEntity();
        entity.type = type;
        entity.orderId = orderId;
        entity.paymentLineId = paymentLineId;
        entity.idempotencyKey = idempotencyKey;
        entity.status = Status.PENDING;
        entity.retryCount = 0;
        entity.nextRetryAt = LocalDateTime.now().minusSeconds(1);
        return entity;
    }

    public ReconcileItem toItem() {
        return new ReconcileItem(
                id,
                type,
                orderId,
                paymentLineId,
                idempotencyKey,
                retryCount
        );
    }

    public void markDone() {
        this.status = Status.DONE;
    }

    public void markFailed(
            final String reason
    ) {
        this.status = Status.FAILED;
        this.failureReason = reason;
    }

    public void scheduleRetry(
            final LocalDateTime nextRetryAt
    ) {
        this.nextRetryAt = nextRetryAt;
        this.retryCount++;
    }
}
