package com.flashsale.infrastructure.jpa.reconcile;

import com.flashsale.infrastructure.jpa.reconcile.PaymentReconcileQueueJpaEntity.Status;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PaymentReconcileQueueJpaRepository
        extends JpaRepository<PaymentReconcileQueueJpaEntity, Long> {

    @Query("SELECT q FROM PaymentReconcileQueueJpaEntity q " +
           "WHERE q.status = :status AND q.nextRetryAt <= :now " +
           "ORDER BY q.id ASC")
    List<PaymentReconcileQueueJpaEntity> findReadyToRetry(
            @Param("status") final Status status,
            @Param("now") final LocalDateTime now,
            final Pageable pageable
    );
}
