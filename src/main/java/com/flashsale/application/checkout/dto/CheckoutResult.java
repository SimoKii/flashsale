package com.flashsale.application.checkout.dto;

public record CheckoutResult(
        ProductInfo product,
        UserPointInfo userPoint,
        StockInfo stock
) {

    public record ProductInfo(
            Long id,
            String name,
            long price,
            String checkinAt,
            String checkoutAt,
            boolean onSale
    ) {}

    public record UserPointInfo(long balance) {}

    public record StockInfo(int remaining) {}
}
