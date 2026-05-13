package com.flashsale.infrastructure.jpa.order;

import com.flashsale.domain.order.IdempotencyKey;
import com.flashsale.domain.order.Order;
import com.flashsale.domain.order.OrderStatus;
import com.flashsale.domain.shared.Money;
import com.flashsale.infrastructure.jpa.base.BaseJpaEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderJpaEntity extends BaseJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "product_id", nullable = false)
    private long productId;

    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PaymentLineJpaEntity> paymentLines = new ArrayList<>();

    public static OrderJpaEntity from(
            final Order order
    ) {
        OrderJpaEntity entity = new OrderJpaEntity();
        entity.id = order.getId();
        entity.idempotencyKey = order.getIdempotencyKey().value();
        entity.userId = order.getUserId();
        entity.productId = order.getProductId();
        entity.totalAmount = order.getTotalAmount().amount();
        entity.status = order.getStatus();
        entity.responseBody = order.getResponseBody();
        entity.expiresAt = order.getExpiresAt();
        order.getPaymentLines().forEach(line -> entity.paymentLines.add(PaymentLineJpaEntity.of(line, entity)));
        return entity;
    }

    public Order toDomain() {
        return Order.restore(
                id,
                IdempotencyKey.of(idempotencyKey),
                userId,
                productId,
                Money.of(totalAmount),
                status,
                expiresAt,
                responseBody,
                paymentLines.stream().map(PaymentLineJpaEntity::toDomain).toList(),
                getCreatedAt()
        );
    }
}
