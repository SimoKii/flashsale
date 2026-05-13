package com.flashsale.infrastructure.jpa.payment;

import com.flashsale.application.booking.port.PaymentEventType;
import com.flashsale.infrastructure.jpa.base.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentEventJpaEntity extends BaseJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_line_id", nullable = false)
    private Long paymentLineId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private PaymentEventType type;

    @Column(name = "pg_tx_id", length = 100)
    private String pgTxId;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    public static PaymentEventJpaEntity of(
            final Long paymentLineId,
            final PaymentEventType type,
            final String pgTxId,
            final String responseBody
    ) {
        PaymentEventJpaEntity entity = new PaymentEventJpaEntity();
        entity.paymentLineId = paymentLineId;
        entity.type = type;
        entity.pgTxId = pgTxId;
        entity.responseBody = responseBody;
        return entity;
    }
}
