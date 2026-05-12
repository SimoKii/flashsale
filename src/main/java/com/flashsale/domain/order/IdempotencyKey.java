package com.flashsale.domain.order;

import com.flashsale.common.exception.DomainException;

import java.util.regex.Pattern;

public record IdempotencyKey(String value) {

    private static final Pattern VALID = Pattern.compile("^[a-zA-Z0-9\\-]{8,64}$");

    public IdempotencyKey {
        if (value == null || value.isBlank() || !VALID.matcher(value).matches()) {
            throw new DomainException("Invalid idempotency key: " + value);
        }
    }

    public static IdempotencyKey of(
            final String value
    ) {
        return new IdempotencyKey(value);
    }
}
