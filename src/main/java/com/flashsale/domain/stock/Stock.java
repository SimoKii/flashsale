package com.flashsale.domain.stock;

import com.flashsale.common.exception.DomainException;
import lombok.Getter;

@Getter
public class Stock {

    private final long productId;
    private final int total;
    private int reserved;
    private int sold;
    private final long version;

    private Stock(
            final long productId,
            final int total,
            final int reserved,
            final int sold,
            final long version
    ) {
        if (productId <= 0) throw new DomainException("productId must be positive");
        if (total < 0) throw new DomainException("total must be non-negative");
        if (reserved < 0) throw new DomainException("reserved must be non-negative");
        if (sold < 0) throw new DomainException("sold must be non-negative");
        if (sold + reserved > total) throw new DomainException("sold + reserved exceeds total");
        this.productId = productId;
        this.total = total;
        this.reserved = reserved;
        this.sold = sold;
        this.version = version;
    }

    public static Stock of(
            final long productId,
            final int total
    ) {
        return new Stock(
                productId,
                total,
                0,
                0,
                0
        );
    }

    public static Stock restore(
            final long productId,
            final int total,
            final int reserved,
            final int sold,
            final long version
    ) {
        return new Stock(
                productId,
                total,
                reserved,
                sold,
                version
        );
    }

    public boolean canReserve() {
        return sold + reserved < total;
    }

    public void reserve() {
        if (!canReserve()) {
            throw new DomainException("Stock exhausted for product: " + productId);
        }
        reserved++;
    }

    public void restore() {
        if (reserved <= 0) throw new DomainException("Nothing to restore");
        reserved--;
    }

    public void confirm() {
        if (reserved <= 0) throw new DomainException("Nothing to confirm");
        reserved--;
        sold++;
    }

    public int remaining() {
        return total - reserved - sold;
    }
}
