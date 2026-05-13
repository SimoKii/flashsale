package com.flashsale.application.booking.dto;

public sealed interface BookingStatusResult permits BookingStatusResult.Paid, BookingStatusResult.Failed, BookingStatusResult.Uncertain, BookingStatusResult.Pending, BookingStatusResult.NotFound {

    record Paid(Long orderId, String responseBody) implements BookingStatusResult {}

    record Failed(String code, String message) implements BookingStatusResult {}

    record Uncertain(String message) implements BookingStatusResult {}

    record Pending() implements BookingStatusResult {}

    record NotFound() implements BookingStatusResult {}
}
