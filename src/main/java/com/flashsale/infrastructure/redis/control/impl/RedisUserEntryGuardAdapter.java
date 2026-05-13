package com.flashsale.infrastructure.redis.control.impl;

import com.flashsale.application.booking.port.UserEntryGuardPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
@RequiredArgsConstructor
public class RedisUserEntryGuardAdapter implements UserEntryGuardPort {

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean acquire(
            final Long productId,
            final Long userId,
            final Duration ttl
    ) {
        Boolean result = redisTemplate.opsForValue()
                .setIfAbsent(ticketOwnerKey(productId, userId), "1", ttl);
        return Boolean.TRUE.equals(result);
    }

    @Override
    public void release(
            final Long productId,
            final Long userId
    ) {
        redisTemplate.delete(ticketOwnerKey(productId, userId));
    }

    private String ticketOwnerKey(
            final Long productId,
            final Long userId
    ) {
        return "ticket_owner:product:" + productId + ":" + userId;
    }
}
