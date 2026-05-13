package com.flashsale.application.booking.dto;

public sealed interface PaymentResult permits PaymentResult.Success, PaymentResult.Failure, PaymentResult.Unknown {

    record Success(String pgTxId) implements PaymentResult {}

    record Failure(
            String reasonCode,
            String message
    ) implements PaymentResult {}

    record Unknown(String idempotencyKey) implements PaymentResult {}
}
