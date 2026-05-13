package com.flashsale.infrastructure.jpa.order.impl;

import com.flashsale.application.booking.port.PaymentLineRepository;
import com.flashsale.common.exception.DomainException;
import com.flashsale.domain.order.PaymentLine;
import com.flashsale.infrastructure.jpa.order.OrderJpaEntity;
import com.flashsale.infrastructure.jpa.order.OrderJpaRepository;
import com.flashsale.infrastructure.jpa.order.PaymentLineJpaEntity;
import com.flashsale.infrastructure.jpa.order.PaymentLineJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PaymentLineRepositoryImpl implements PaymentLineRepository {

    private final PaymentLineJpaRepository jpaRepository;
    private final OrderJpaRepository orderJpaRepository;

    @Override
    @Transactional
    public PaymentLine save(
            final PaymentLine line,
            final Long orderId
    ) {
        OrderJpaEntity order = orderJpaRepository.findById(orderId)
                .orElseThrow(() -> new DomainException("Order not found: " + orderId));
        PaymentLineJpaEntity entity = PaymentLineJpaEntity.of(line, order);
        PaymentLineJpaEntity saved = jpaRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentLine> findByOrderId(
            final Long orderId
    ) {
        return jpaRepository.findByOrder_Id(orderId).stream()
                .map(PaymentLineJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentLine> findById(
            final Long id
    ) {
        return jpaRepository.findById(id).map(PaymentLineJpaEntity::toDomain);
    }
}
