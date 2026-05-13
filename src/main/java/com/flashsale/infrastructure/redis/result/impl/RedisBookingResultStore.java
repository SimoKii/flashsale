package com.flashsale.infrastructure.redis.result.impl;

import com.flashsale.application.booking.port.BookingResultStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RedisBookingResultStore implements BookingResultStore {

    private final StringRedisTemplate redisTemplate;

    @Override
    public void save(
            final Long productId,
            final String ticketId,
            final String body,
            final Duration ttl
    ) {
        redisTemplate.opsForValue().set(resultKey(productId, ticketId), body, ttl);
    }

    @Override
    public Optional<String> find(
            final Long productId,
            final String ticketId
    ) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(resultKey(productId, ticketId)));
    }

    private String resultKey(
            final Long productId,
            final String ticketId
    ) {
        return "result:product:" + productId + ":" + ticketId;
    }
}
