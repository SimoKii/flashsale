package com.flashsale.infrastructure.redis.stock.impl;

import com.flashsale.application.booking.port.StockPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public class RedisStockAdapter implements StockPort {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> reserveScript;
    private final DefaultRedisScript<Long> restoreScript;

    public RedisStockAdapter(
            final StringRedisTemplate redisTemplate,
            @Qualifier("atomicReserveScript") final DefaultRedisScript<Long> reserveScript,
            @Qualifier("atomicRestoreScript") final DefaultRedisScript<Long> restoreScript
    ) {
        this.redisTemplate = redisTemplate;
        this.reserveScript = reserveScript;
        this.restoreScript = restoreScript;
    }

    @Override
    public int reserve(
            final Long productId,
            final String ticketId,
            final Long userId
    ) {
        Long result = redisTemplate.execute(
                reserveScript,
                List.of(
                        stockKey(productId),
                        holdersKey(productId),
                        processedKey(productId)
                ),
                ticketId,
                String.valueOf(userId),
                String.valueOf(Instant.now().getEpochSecond())
        );
        return result == null ? -1 : result.intValue();
    }

    @Override
    public void restore(
            final Long productId,
            final String ticketId,
            final Long userId
    ) {
        redisTemplate.execute(
                restoreScript,
                List.of(
                        stockKey(productId),
                        holdersKey(productId)
                ),
                String.valueOf(userId)
        );
    }

    @Override
    public void confirm(
            final Long productId,
            final String ticketId
    ) {
    }

    @Override
    public int remaining(
            final Long productId
    ) {
        String value = redisTemplate.opsForValue().get(stockKey(productId));
        return value == null ? 0 : Integer.parseInt(value);
    }

    private String stockKey(
            final Long productId
    ) {
        return "stock:product:" + productId;
    }

    private String holdersKey(
            final Long productId
    ) {
        return "holders:product:" + productId;
    }

    private String processedKey(
            final Long productId
    ) {
        return "ticket_processed:product:" + productId;
    }
}
