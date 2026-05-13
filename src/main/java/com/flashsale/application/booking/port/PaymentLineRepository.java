package com.flashsale.application.booking.port;

import com.flashsale.domain.order.PaymentLine;

import java.util.List;
import java.util.Optional;

public interface PaymentLineRepository {

    PaymentLine save(PaymentLine line, Long orderId);

    List<PaymentLine> findByOrderId(Long orderId);

    Optional<PaymentLine> findById(Long id);
}
