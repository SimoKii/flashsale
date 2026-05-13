package com.flashsale.infrastructure.jpa.stock;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StockJpaRepository extends JpaRepository<StockJpaEntity, Long> {

    Optional<StockJpaEntity> findByProductId(Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StockJpaEntity s WHERE s.productId = :productId")
    Optional<StockJpaEntity> findByProductIdWithLock(@Param("productId") Long productId);
}
