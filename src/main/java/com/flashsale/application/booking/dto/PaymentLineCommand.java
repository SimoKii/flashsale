package com.flashsale.application.booking.dto;

import com.flashsale.domain.order.PaymentMethodCode;

import java.util.Objects;

public record PaymentLineCommand(
        int sequence,
        PaymentMethodCode method,
        long amount,
        String cardNumber,
        String idempotencyKey,
        Long userId
) {
    public PaymentLineCommand {
        Objects.requireNonNull(method, "method must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
    }
}
