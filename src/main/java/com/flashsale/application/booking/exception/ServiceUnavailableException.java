package com.flashsale.application.booking.exception;

public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(final String message) {
        super(message);
    }
}
