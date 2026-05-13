package com.flashsale.application.booking.dto;

public record BookingAcceptedResult(String ticketId, long queuePosition) {
    public BookingAcceptedResult {
        java.util.Objects.requireNonNull(ticketId, "ticketId must not be null");
        if (queuePosition < 0) throw new IllegalArgumentException("queuePosition must not be negative");
    }
}
