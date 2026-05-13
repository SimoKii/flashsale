package com.flashsale.infrastructure.jpa.point;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PointJpaRepository extends JpaRepository<PointAccountJpaEntity, Long> {

    Optional<PointAccountJpaEntity> findByUserId(final Long userId);
}
