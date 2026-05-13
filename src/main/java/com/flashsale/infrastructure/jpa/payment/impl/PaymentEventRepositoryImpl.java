package com.flashsale.infrastructure.jpa.payment.impl;

import com.flashsale.application.booking.port.PaymentEventRepository;
import com.flashsale.application.booking.port.PaymentEventType;
import com.flashsale.infrastructure.jpa.payment.PaymentEventJpaEntity;
import com.flashsale.infrastructure.jpa.payment.PaymentEventJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class PaymentEventRepositoryImpl implements PaymentEventRepository {

    private final PaymentEventJpaRepository jpaRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(
            final Long paymentLineId,
            final PaymentEventType type,
            final String pgTxId,
            final String responseBody
    ) {
        jpaRepository.save(PaymentEventJpaEntity.of(
                paymentLineId,
                type,
                pgTxId,
                responseBody
        ));
    }
}
