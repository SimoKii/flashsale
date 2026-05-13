package com.flashsale.infrastructure.jpa.point;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PointTxJpaRepository extends JpaRepository<PointTxJpaEntity, Long> {

    boolean existsByIdempotencyKey(String idempotencyKey);
}
