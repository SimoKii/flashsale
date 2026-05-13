package com.flashsale.infrastructure.jpa.order;

import com.flashsale.domain.order.PaymentLine;
import com.flashsale.domain.order.PaymentLineStatus;
import com.flashsale.domain.order.PaymentMethodCode;
import com.flashsale.domain.shared.Money;
import com.flashsale.infrastructure.jpa.base.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment_line")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentLineJpaEntity extends BaseJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderJpaEntity order;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method_code", nullable = false, length = 20)
    private PaymentMethodCode paymentMethodCode;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentLineStatus status;

    @Column(nullable = false)
    private int sequence;

    @Column(name = "pg_tx_id", length = 100)
    private String pgTxId;

    public static PaymentLineJpaEntity of(
            final PaymentLine line,
            final OrderJpaEntity order
    ) {
        PaymentLineJpaEntity entity = new PaymentLineJpaEntity();
        entity.id = line.getId();
        entity.order = order;
        entity.paymentMethodCode = line.getMethod();
        entity.amount = line.getAmount().amount();
        entity.status = line.getStatus();
        entity.sequence = line.getSequence();
        entity.pgTxId = line.getPgTxId();
        return entity;
    }

    public PaymentLine toDomain() {
        return PaymentLine.restore(
                id,
                paymentMethodCode,
                Money.of(amount),
                status,
                pgTxId,
                sequence
        );
    }
}
