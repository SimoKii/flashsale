package com.flashsale.application.point;

public interface PointService {

    void deduct(
            final long userId,
            final long amount,
            final String idempotencyKey
    );

    void refund(
            final long userId,
            final long amount,
            final String idempotencyKey
    );

    long findBalance(final long userId);
}
