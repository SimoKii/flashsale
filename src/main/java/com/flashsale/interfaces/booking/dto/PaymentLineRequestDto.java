package com.flashsale.interfaces.booking.dto;

public record PaymentLineRequestDto(
        int sequence,
        String method,
        long amount,
        String cardNumber,
        String idempotencyKey
) {}
