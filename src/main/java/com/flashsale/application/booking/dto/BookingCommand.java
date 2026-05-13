package com.flashsale.application.booking.dto;

import java.util.List;
import java.util.Objects;

public record BookingCommand(
        Long productId,
        Long userId,
        String idempotencyKey,
        long totalAmount,
        List<PaymentLineCommand> paymentLines
) {
    public BookingCommand {
        Objects.requireNonNull(productId, "productId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        if (idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey must not be blank");
        Objects.requireNonNull(paymentLines, "paymentLines must not be null");
        if (totalAmount <= 0) throw new IllegalArgumentException("totalAmount must be positive");
        if (paymentLines.isEmpty()) throw new IllegalArgumentException("paymentLines must not be empty");
    }
}
