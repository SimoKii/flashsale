package com.flashsale.application.booking.port;

import java.time.Duration;
import java.util.Optional;

public interface IdempotencyStore {

    boolean setIfAbsent(
            final String key,
            final String value,
            final Duration ttl
    );

    Optional<String> get(final String key);

    void update(
            final String key,
            final String value,
            final Duration ttl
    );
}
