package com.flashsale.infrastructure.redis.idempotency.impl;

import com.flashsale.application.booking.port.IdempotencyStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RedisIdempotencyStore implements IdempotencyStore {

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean setIfAbsent(
            final String key,
            final String value,
            final Duration ttl
    ) {
        Boolean result = redisTemplate.opsForValue().setIfAbsent(key, value, ttl);
        return Boolean.TRUE.equals(result);
    }

    @Override
    public Optional<String> get(
            final String key
    ) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }

    @Override
    public void update(
            final String key,
            final String value,
            final Duration ttl
    ) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }
}
