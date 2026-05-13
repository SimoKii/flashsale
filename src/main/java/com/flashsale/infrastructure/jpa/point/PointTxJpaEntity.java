package com.flashsale.infrastructure.jpa.point;

import com.flashsale.domain.point.PointTx;
import com.flashsale.domain.shared.Money;
import com.flashsale.infrastructure.jpa.base.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "point_tx",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_point_tx_idempotency_key",
                columnNames = "idempotency_key"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointTxJpaEntity extends BaseJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "order_id")
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PointTx.Type type;

    @Column(nullable = false)
    private long amount;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    public static PointTxJpaEntity from(
            final PointTx tx
    ) {
        PointTxJpaEntity entity = new PointTxJpaEntity();
        entity.userId = tx.getUserId();
        entity.orderId = tx.getOrderId();
        entity.type = tx.getType();
        entity.amount = tx.getAmount().amount();
        entity.idempotencyKey = tx.getIdempotencyKey();
        return entity;
    }

    public PointTx toDomain() {
        return PointTx.restore(
                id,
                userId,
                orderId,
                type,
                Money.of(amount),
                idempotencyKey,
                getCreatedAt()
        );
    }
}
