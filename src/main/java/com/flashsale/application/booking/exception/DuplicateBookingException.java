package com.flashsale.application.booking.exception;

public class DuplicateBookingException extends RuntimeException {

    public DuplicateBookingException(final String message) {
        super(message);
    }
}
