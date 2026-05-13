package com.flashsale.application.booking.port;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public interface BookingQueuePort {

    String enqueue(
            final Long productId,
            final Map<String, String> fields
    );

    List<QueueMessage> read(
            final Long productId,
            final String consumerGroup,
            final String consumer,
            final int count
    );

    List<QueueMessage> claim(
            final Long productId,
            final String consumerGroup,
            final String consumer,
            final Duration minIdleTime,
            final int count
    );

    void ack(
            final Long productId,
            final String consumerGroup,
            final String messageId
    );

    void sendToDlq(
            final Long productId,
            final Map<String, String> fields
    );

    long pendingDeliveryCount(
            final Long productId,
            final String consumerGroup,
            final String messageId
    );

    record QueueMessage(
            String id,
            Map<String, String> fields,
            long deliveryCount
    ) {}
}
