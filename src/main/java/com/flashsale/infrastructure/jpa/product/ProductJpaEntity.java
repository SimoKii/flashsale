package com.flashsale.infrastructure.jpa.product;

import com.flashsale.domain.product.Product;
import com.flashsale.domain.shared.Money;
import com.flashsale.infrastructure.jpa.base.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductJpaEntity extends BaseJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private long price;

    @Column(name = "checkin_at", nullable = false)
    private LocalDateTime checkinAt;

    @Column(name = "checkout_at", nullable = false)
    private LocalDateTime checkoutAt;

    @Column(name = "sale_open_at", nullable = false)
    private LocalDateTime saleOpenAt;

    public Product toDomain() {
        return Product.restore(
                id,
                name,
                Money.of(price),
                checkinAt,
                checkoutAt,
                saleOpenAt
        );
    }
}
