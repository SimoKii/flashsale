package com.flashsale.infrastructure.jpa.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentLineJpaRepository extends JpaRepository<PaymentLineJpaEntity, Long> {
    List<PaymentLineJpaEntity> findByOrder_Id(Long orderId);
}
