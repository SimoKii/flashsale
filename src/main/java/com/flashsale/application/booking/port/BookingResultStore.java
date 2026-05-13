package com.flashsale.application.booking.port;

import java.time.Duration;
import java.util.Optional;

public interface BookingResultStore {

    void save(
            final Long productId,
            final String ticketId,
            final String body,
            final Duration ttl
    );

    Optional<String> find(
            final Long productId,
            final String ticketId
    );
}
