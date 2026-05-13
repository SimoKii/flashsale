package com.flashsale.infrastructure.jpa.order.impl;

import com.flashsale.application.booking.port.OrderRepository;
import com.flashsale.domain.order.Order;
import com.flashsale.infrastructure.jpa.order.OrderJpaEntity;
import com.flashsale.infrastructure.jpa.order.OrderJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository jpaRepository;

    @Override
    public Order save(
            final Order order
    ) {
        OrderJpaEntity entity = OrderJpaEntity.from(order);
        OrderJpaEntity saved = jpaRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    public Optional<Order> findById(
            final Long id
    ) {
        return jpaRepository.findById(id).map(OrderJpaEntity::toDomain);
    }

    @Override
    public Optional<Order> findByIdempotencyKey(
            final String idempotencyKey
    ) {
        return jpaRepository.findByIdempotencyKey(idempotencyKey).map(OrderJpaEntity::toDomain);
    }
}
