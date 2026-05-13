package com.flashsale.infrastructure.jpa.payment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentEventJpaRepository extends JpaRepository<PaymentEventJpaEntity, Long> {
}
