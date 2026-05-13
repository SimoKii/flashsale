package com.flashsale.infrastructure.jpa.reconcile.impl;

import com.flashsale.application.booking.port.PaymentReconcileQueue;
import com.flashsale.common.exception.DomainException;
import com.flashsale.infrastructure.jpa.reconcile.PaymentReconcileQueueJpaEntity;
import com.flashsale.infrastructure.jpa.reconcile.PaymentReconcileQueueJpaEntity.Status;
import com.flashsale.infrastructure.jpa.reconcile.PaymentReconcileQueueJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PaymentReconcileQueueImpl implements PaymentReconcileQueue {

    private final PaymentReconcileQueueJpaRepository jpaRepository;

    @Override
    @Transactional
    public void enqueue(
            final ReconcileType type,
            final Long orderId,
            final Long paymentLineId,
            final String idempotencyKey
    ) {
        jpaRepository.saveAndFlush(
                PaymentReconcileQueueJpaEntity.create(
                        type,
                        orderId,
                        paymentLineId,
                        idempotencyKey
                )
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReconcileItem> findReadyToRetry(
            final int limit
    ) {
        return jpaRepository.findReadyToRetry(
                        Status.PENDING,
                        LocalDateTime.now(),
                        PageRequest.of(0, limit)
                )
                .stream()
                .map(PaymentReconcileQueueJpaEntity::toItem)
                .toList();
    }

    @Override
    @Transactional
    public void markDone(
            final Long id
    ) {
        findEntityById(id).markDone();
    }

    @Override
    @Transactional
    public void markFailed(
            final Long id,
            final String reason
    ) {
        findEntityById(id).markFailed(reason);
    }

    @Override
    @Transactional
    public void scheduleRetry(
            final Long id,
            final LocalDateTime nextRetryAt
    ) {
        findEntityById(id).scheduleRetry(nextRetryAt);
    }

    private PaymentReconcileQueueJpaEntity findEntityById(
            final Long id
    ) {
        return jpaRepository.findById(id)
                .orElseThrow(() -> new DomainException("ReconcileQueue item not found: " + id));
    }
}