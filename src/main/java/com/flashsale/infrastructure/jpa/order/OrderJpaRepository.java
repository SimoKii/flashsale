package com.flashsale.infrastructure.jpa.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, Long> {

    Optional<OrderJpaEntity> findByIdempotencyKey(String idempotencyKey);
}
