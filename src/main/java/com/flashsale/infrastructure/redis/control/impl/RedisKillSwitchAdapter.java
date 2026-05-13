package com.flashsale.infrastructure.redis.control.impl;

import com.flashsale.application.booking.port.KillSwitchPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
@RequiredArgsConstructor
public class RedisKillSwitchAdapter implements KillSwitchPort {

    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean isOn(
            final Long productId
    ) {
        return redisTemplate.hasKey(killSwitchKey(productId));
    }

    @Override
    public void turnOn(
            final Long productId,
            final String reason
    ) {
        redisTemplate.opsForValue().set(killSwitchKey(productId), reason, DEFAULT_TTL);
    }

    @Override
    public void turnOff(
            final Long productId
    ) {
        redisTemplate.delete(killSwitchKey(productId));
    }

    private String killSwitchKey(
            final Long productId
    ) {
        return "kill_switch:product:" + productId;
    }
}
