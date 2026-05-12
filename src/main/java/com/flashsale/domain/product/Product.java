package com.flashsale.domain.product;

import com.flashsale.common.exception.DomainException;
import com.flashsale.domain.shared.Money;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
public class Product {

    private final long id;
    private final String name;
    private final Money price;
    private final LocalDateTime checkinAt;
    private final LocalDateTime checkoutAt;
    private final LocalDateTime saleOpenAt;

    private Product(
            final long id,
            final String name,
            final Money price,
            final LocalDateTime checkinAt,
            final LocalDateTime checkoutAt,
            final LocalDateTime saleOpenAt
    ) {
        if (name == null || name.isBlank()) throw new DomainException("Product name must not be blank");
        Objects.requireNonNull(price, "price must not be null");
        Objects.requireNonNull(checkinAt, "checkinAt must not be null");
        Objects.requireNonNull(checkoutAt, "checkoutAt must not be null");
        Objects.requireNonNull(saleOpenAt, "saleOpenAt must not be null");
        if (!checkoutAt.isAfter(checkinAt)) throw new DomainException("checkoutAt must be after checkinAt");
        this.id = id;
        this.name = name;
        this.price = price;
        this.checkinAt = checkinAt;
        this.checkoutAt = checkoutAt;
        this.saleOpenAt = saleOpenAt;
    }

    public static Product restore(
            final long id,
            final String name,
            final Money price,
            final LocalDateTime checkinAt,
            final LocalDateTime checkoutAt,
            final LocalDateTime saleOpenAt
    ) {
        return new Product(
                id,
                name,
                price,
                checkinAt,
                checkoutAt,
                saleOpenAt
        );
    }

    public boolean isOnSale(
            final LocalDateTime now
    ) {
        return !now.isBefore(saleOpenAt);
    }
}
