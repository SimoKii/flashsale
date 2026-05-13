package com.flashsale.domain.point;

import com.flashsale.common.exception.DomainException;
import com.flashsale.domain.shared.Money;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
public class PointTx {

    public enum Type { USE, REFUND, EARN }

    private final Long id;
    private final long userId;
    private final Long orderId;
    private final Type type;
    private final Money amount;
    private final String idempotencyKey;
    private final LocalDateTime createdAt;

    private PointTx(
            final Long id,
            final long userId,
            final Long orderId,
            final Type type,
            final Money amount,
            final String idempotencyKey,
            final LocalDateTime createdAt
    ) {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.amount() <= 0) throw new DomainException("PointTx amount must be positive");
        this.id = id;
        this.userId = userId;
        this.orderId = orderId;
        this.type = type;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
    }

    public static PointTx of(
            final long userId,
            final Long orderId,
            final Type type,
            final Money amount,
            final String idempotencyKey
    ) {
        return new PointTx(
                null,
                userId,
                orderId,
                type,
                amount,
                idempotencyKey,
                LocalDateTime.now()
        );
    }

    public static PointTx of(
            final long userId,
            final Long orderId,
            final Type type,
            final Money amount
    ) {
        return of(userId, orderId, type, amount, null);
    }

    public static PointTx restore(
            final Long id,
            final long userId,
            final Long orderId,
            final Type type,
            final Money amount,
            final LocalDateTime createdAt
    ) {
        return new PointTx(
                id,
                userId,
                orderId,
                type,
                amount,
                null,
                createdAt
        );
    }

    public static PointTx restore(
            final Long id,
            final long userId,
            final Long orderId,
            final Type type,
            final Money amount,
            final String idempotencyKey,
            final LocalDateTime createdAt
    ) {
        return new PointTx(
                id,
                userId,
                orderId,
                type,
                amount,
                idempotencyKey,
                createdAt
        );
    }
}
