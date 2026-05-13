package com.flashsale.application.booking.port;

import java.time.Duration;

public interface UserEntryGuardPort {

    boolean acquire(
            final Long productId,
            final Long userId,
            final Duration ttl
    );

    void release(
            final Long productId,
            final Long userId
    );
}
