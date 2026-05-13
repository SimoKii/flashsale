package com.flashsale.domain.order;

import com.flashsale.common.exception.DomainException;
import com.flashsale.domain.shared.Money;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Getter
public class Order {

    private Long id;
    private final IdempotencyKey idempotencyKey;
    private final long userId;
    private final long productId;
    private final Money totalAmount;
    private OrderStatus status;
    private final LocalDateTime expiresAt;
    private String responseBody;
    private final List<PaymentLine> paymentLines;
    private final LocalDateTime createdAt;

    private Order(
            final Long id,
            final IdempotencyKey idempotencyKey,
            final long userId,
            final long productId,
            final Money totalAmount,
            final OrderStatus status,
            final LocalDateTime expiresAt,
            final String responseBody,
            final List<PaymentLine> paymentLines,
            final LocalDateTime createdAt
    ) {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        Objects.requireNonNull(totalAmount, "totalAmount must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (userId <= 0) throw new DomainException("userId must be positive");
        if (productId <= 0) throw new DomainException("productId must be positive");
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.userId = userId;
        this.productId = productId;
        this.totalAmount = totalAmount;
        this.status = status;
        this.expiresAt = expiresAt;
        this.responseBody = responseBody;
        this.paymentLines = new ArrayList<>(paymentLines);
        this.createdAt = createdAt;
    }

    public static Order create(
            final IdempotencyKey idempotencyKey,
            final long userId,
            final long productId,
            final Money totalAmount,
            final LocalDateTime expiresAt
    ) {
        return new Order(
                null,
                idempotencyKey,
                userId,
                productId,
                totalAmount,
                OrderStatus.PENDING,
                expiresAt,
                null,
                List.of(),
                LocalDateTime.now()
        );
    }

    public static Order restore(
            final Long id,
            final IdempotencyKey idempotencyKey,
            final long userId,
            final long productId,
            final Money totalAmount,
            final OrderStatus status,
            final LocalDateTime expiresAt,
            final String responseBody,
            final List<PaymentLine> paymentLines,
            final LocalDateTime createdAt
    ) {
        return new Order(
                id,
                idempotencyKey,
                userId,
                productId,
                totalAmount,
                status,
                expiresAt,
                responseBody,
                paymentLines,
                createdAt
        );
    }

    public void markPaid(
            final String responseBody
    ) {
        if (status != OrderStatus.PENDING) {
            throw new DomainException("Cannot mark PAID from status: " + status);
        }
        this.status = OrderStatus.PAID;
        this.responseBody = responseBody;
    }

    public void markFailed(
            final String responseBody
    ) {
        if (status != OrderStatus.PENDING && status != OrderStatus.COMPENSATING) {
            throw new DomainException("Cannot mark FAILED from status: " + status);
        }
        this.status = OrderStatus.FAILED;
        this.responseBody = responseBody;
    }

    public void markUncertain(
            final String responseBody
    ) {
        if (status != OrderStatus.PENDING) {
            throw new DomainException("Cannot mark UNCERTAIN from status: " + status);
        }
        this.status = OrderStatus.UNCERTAIN;
        this.responseBody = responseBody;
    }

    public void markCompensating() {
        if (status != OrderStatus.PENDING) {
            throw new DomainException("Cannot mark COMPENSATING from status: " + status);
        }
        this.status = OrderStatus.COMPENSATING;
    }

    public void markCanceled() {
        if (status != OrderStatus.COMPENSATING) {
            throw new DomainException("Cannot mark CANCELED from status: " + status);
        }
        this.status = OrderStatus.CANCELED;
    }

    public void addPaymentLine(
            final PaymentLine line
    ) {
        Objects.requireNonNull(line, "PaymentLine must not be null");
        if (status != OrderStatus.PENDING) {
            throw new DomainException("Cannot add PaymentLine when order status is: " + status);
        }
        paymentLines.add(line);
    }

    public void assignId(
            final Long id
    ) {
        if (this.id != null) throw new DomainException("ID already assigned");
        this.id = id;
    }

    public void approvePaymentLine(
            final int sequence,
            final String pgTxId
    ) {
        paymentLineBySequence(sequence).markApproved(pgTxId);
    }

    public void declinePaymentLine(
            final int sequence
    ) {
        paymentLineBySequence(sequence).markDeclined();
    }

    public void cancelPaymentLine(
            final int sequence
    ) {
        paymentLineBySequence(sequence).markCanceled();
    }

    public void pendCancelPaymentLine(
            final int sequence
    ) {
        paymentLineBySequence(sequence).markCancelPending();
    }

    public void failCancelPaymentLine(
            final int sequence
    ) {
        paymentLineBySequence(sequence).markCancelFailed();
    }

    public void uncertainPaymentLine(
            final int sequence
    ) {
        paymentLineBySequence(sequence).markUncertain();
    }

    public void assignPaymentLineId(
            final int sequence,
            final Long lineId
    ) {
        paymentLineBySequence(sequence).assignId(lineId);
    }

    private PaymentLine paymentLineBySequence(
            final int sequence
    ) {
        return paymentLines.stream()
                .filter(l -> l.getSequence() == sequence)
                .findFirst()
                .orElseThrow(() -> new DomainException("PaymentLine not found: seq=" + sequence));
    }

    public List<PaymentLine> getPaymentLines() {
        return Collections.unmodifiableList(paymentLines);
    }
}