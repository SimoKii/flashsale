package com.flashsale.infrastructure.redis.queue.impl;

import com.flashsale.application.booking.port.BookingQueuePort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisStreamCommands.XClaimOptions;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class RedisBookingQueueAdapter implements BookingQueuePort {

    private static final Duration READ_BLOCK_TIMEOUT = Duration.ofMillis(100);

    private final StringRedisTemplate redisTemplate;

    @Override
    public String enqueue(
            final Long productId,
            final Map<String, String> fields
    ) {
        RecordId id = redisTemplate.opsForStream().add(
                StreamRecords.newRecord()
                        .ofMap(fields)
                        .withStreamKey(queueKey(productId))
        );
        return id == null ? null : id.getValue();
    }

    @Override
    public List<QueueMessage> read(
            final Long productId,
            final String consumerGroup,
            final String consumer,
            final int count
    ) {
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                Consumer.from(consumerGroup, consumer),
                StreamReadOptions.empty().count(count).block(READ_BLOCK_TIMEOUT),
                StreamOffset.create(queueKey(productId), ReadOffset.lastConsumed())
        );
        if (records == null) return List.of();
        return records.stream()
                .map(r -> new QueueMessage(
                        r.getId().getValue(),
                        r.getValue().entrySet().stream()
                                .collect(Collectors.toMap(
                                        e -> e.getKey().toString(),
                                        e -> e.getValue().toString()
                                )),
                        0L
                ))
                .toList();
    }

    @Override
    public List<QueueMessage> claim(
            final Long productId,
            final String consumerGroup,
            final String consumer,
            final Duration minIdleTime,
            final int count
    ) {
        PendingMessages pending = redisTemplate.opsForStream().pending(
                queueKey(productId),
                consumerGroup,
                Range.unbounded(),
                count
        );
        if (pending == null || pending.isEmpty()) return List.of();

        RecordId[] ids = pending.stream()
                .filter(pm -> pm.getElapsedTimeSinceLastDelivery().compareTo(minIdleTime) >= 0)
                .map(PendingMessage::getId)
                .toArray(RecordId[]::new);

        if (ids.length == 0) return List.of();

        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().claim(
                queueKey(productId),
                consumerGroup,
                consumer,
                XClaimOptions.minIdle(minIdleTime).ids(ids)
        );
        if (records == null) return List.of();
        return records.stream()
                .map(r -> new QueueMessage(
                        r.getId().getValue(),
                        r.getValue().entrySet().stream()
                                .collect(Collectors.toMap(
                                        e -> e.getKey().toString(),
                                        e -> e.getValue().toString()
                                )),
                        0L
                ))
                .toList();
    }

    @Override
    public void ack(
            final Long productId,
            final String consumerGroup,
            final String messageId
    ) {
        redisTemplate.opsForStream().acknowledge(queueKey(productId), consumerGroup, messageId);
    }

    @Override
    public void sendToDlq(
            final Long productId,
            final Map<String, String> fields
    ) {
        redisTemplate.opsForStream().add(
                StreamRecords.newRecord()
                        .ofMap(fields)
                        .withStreamKey(dlqKey(productId))
        );
    }

    @Override
    public long pendingDeliveryCount(
            final Long productId,
            final String consumerGroup,
            final String messageId
    ) {
        PendingMessages pending = redisTemplate.opsForStream().pending(
                queueKey(productId),
                consumerGroup,
                Range.closed(messageId, messageId),
                1L
        );
        if (pending == null || pending.isEmpty()) return 0L;
        return pending.get(0).getTotalDeliveryCount();
    }

    private String queueKey(
            final Long productId
    ) {
        return "queue:product:" + productId;
    }

    private String dlqKey(
            final Long productId
    ) {
        return "dlq:product:" + productId;
    }
}
