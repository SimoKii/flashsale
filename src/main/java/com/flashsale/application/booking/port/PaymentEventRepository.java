package com.flashsale.application.booking.port;

public interface PaymentEventRepository {
    void save(
            Long paymentLineId,
            PaymentEventType type,
            String pgTxId,
            String responseBody
    );
}
