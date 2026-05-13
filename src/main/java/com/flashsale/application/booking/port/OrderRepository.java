package com.flashsale.application.booking.port;

import com.flashsale.domain.order.Order;

import java.util.Optional;

public interface OrderRepository {

    Order save(final Order order);

    Optional<Order> findById(final Long id);

    Optional<Order> findByIdempotencyKey(final String idempotencyKey);
}
