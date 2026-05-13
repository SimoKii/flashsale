package com.flashsale.domain.order;

import com.flashsale.common.exception.DomainException;
import com.flashsale.domain.shared.Money;
import lombok.Getter;

import java.util.Objects;

@Getter
public class PaymentLine {

    private Long id;
    private final PaymentMethodCode method;
    private final Money amount;
    private PaymentLineStatus status;
    private String pgTxId;
    private final int sequence;

    private PaymentLine(
            final Long id,
            final PaymentMethodCode method,
            final Money amount,
            final PaymentLineStatus status,
            final String pgTxId,
            final int sequence
    ) {
        this.id = id;
        this.method = method;
        this.amount = amount;
        this.status = status;
        this.pgTxId = pgTxId;
        this.sequence = sequence;
    }

    public static PaymentLine of(
            final PaymentMethodCode method,
            final Money amount
    ) {
        final int seq = method.isPoint() ? 1 : 2;
        return new PaymentLine(
                null,
                method,
                amount,
                PaymentLineStatus.REQUESTED,
                null,
                seq
        );
    }

    public static PaymentLine restore(
            final Long id,
            final PaymentMethodCode method,
            final Money amount,
            final PaymentLineStatus status,
            final String pgTxId,
            final int sequence
    ) {
        return new PaymentLine(
                id,
                method,
                amount,
                status,
                pgTxId,
                sequence
        );
    }

    void markApproved(
            final String pgTxId
    ) {
        Objects.requireNonNull(pgTxId, "pgTxId must not be null");
        if (pgTxId.isBlank()) throw new DomainException("pgTxId must not be blank");
        requireStatus(PaymentLineStatus.REQUESTED, "markApproved");
        this.status = PaymentLineStatus.APPROVED;
        this.pgTxId = pgTxId;
    }

    void markDeclined() {
        requireStatus(PaymentLineStatus.REQUESTED, "markDeclined");
        this.status = PaymentLineStatus.DECLINED;
    }

    void markCanceled() {
        if (status != PaymentLineStatus.APPROVED && status != PaymentLineStatus.CANCEL_PENDING) {
            throw new DomainException("Cannot cancel from status: " + status);
        }
        this.status = PaymentLineStatus.CANCELED;
    }

    void markCancelPending() {
        requireStatus(PaymentLineStatus.APPROVED, "markCancelPending");
        this.status = PaymentLineStatus.CANCEL_PENDING;
    }

    void markCancelFailed() {
        requireStatus(PaymentLineStatus.CANCEL_PENDING, "markCancelFailed");
        this.status = PaymentLineStatus.CANCEL_FAILED;
    }

    void markUncertain() {
        requireStatus(PaymentLineStatus.REQUESTED, "markUncertain");
        this.status = PaymentLineStatus.UNCERTAIN;
    }

    void assignId(
            final Long id
    ) {
        if (this.id != null) throw new DomainException("ID already assigned");
        this.id = id;
    }

    private void requireStatus(
            final PaymentLineStatus required,
            final String operation
    ) {
        if (status != required) {
            throw new DomainException("Cannot " + operation + " from status: " + status);
        }
    }
}