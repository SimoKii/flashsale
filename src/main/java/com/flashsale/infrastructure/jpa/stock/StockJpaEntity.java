package com.flashsale.infrastructure.jpa.stock;

import com.flashsale.domain.stock.Stock;
import com.flashsale.infrastructure.jpa.base.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "stock")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockJpaEntity extends BaseJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, unique = true)
    private long productId;

    @Column(nullable = false)
    private int total;

    @Column(nullable = false)
    private int reserved;

    @Column(nullable = false)
    private int sold;

    @Version
    @Column(nullable = false)
    private Long version;

    public Stock toDomain() {
        return Stock.restore(
                productId,
                total,
                reserved,
                sold,
                version
        );
    }

    public void updateFrom(
            final Stock stock
    ) {
        this.reserved = stock.getReserved();
        this.sold = stock.getSold();
    }
}
