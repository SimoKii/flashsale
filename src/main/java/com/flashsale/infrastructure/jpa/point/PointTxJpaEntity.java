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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "point_tx")
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

    public static PointTxJpaEntity from(
            final PointTx tx
    ) {
        PointTxJpaEntity entity = new PointTxJpaEntity();
        entity.userId = tx.getUserId();
        entity.orderId = tx.getOrderId();
        entity.type = tx.getType();
        entity.amount = tx.getAmount().amount();
        return entity;
    }

    public PointTx toDomain() {
        return PointTx.restore(
                id,
                userId,
                orderId,
                type,
                Money.of(amount),
                getCreatedAt()
        );
    }
}
