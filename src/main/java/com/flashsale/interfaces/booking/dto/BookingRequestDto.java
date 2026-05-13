package com.flashsale.interfaces.booking.dto;

import java.util.List;

public record BookingRequestDto(
        Long productId,
        long totalAmount,
        List<PaymentLineRequestDto> paymentLines
) {}
