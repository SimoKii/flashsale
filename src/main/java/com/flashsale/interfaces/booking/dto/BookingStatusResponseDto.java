package com.flashsale.interfaces.booking.dto;

import com.flashsale.application.booking.dto.BookingStatusResult;

public record BookingStatusResponseDto(
        String status,
        Long orderId,
        String responseBody,
        String code,
        String message
) {

    public static BookingStatusResponseDto from(
            BookingStatusResult result
    ) {
        if (result instanceof BookingStatusResult.Paid p) {
            return new BookingStatusResponseDto("PAID", p.orderId(), p.responseBody(), null, null);
        } else if (result instanceof BookingStatusResult.Failed f) {
            return new BookingStatusResponseDto("FAILED", null, null, f.code(), f.message());
        } else if (result instanceof BookingStatusResult.Uncertain u) {
            return new BookingStatusResponseDto("UNCERTAIN", null, null, null, u.message());
        } else if (result instanceof BookingStatusResult.Pending) {
            return new BookingStatusResponseDto("PENDING", null, null, null, null);
        } else {
            return new BookingStatusResponseDto("NOT_FOUND", null, null, null, null);
        }
    }
}
