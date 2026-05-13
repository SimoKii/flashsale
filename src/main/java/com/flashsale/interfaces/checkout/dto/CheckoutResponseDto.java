package com.flashsale.interfaces.checkout.dto;

import com.flashsale.application.checkout.dto.CheckoutResult;

public record CheckoutResponseDto(
        ProductDto product,
        UserPointDto userPoint,
        StockDto stock
) {

    public static CheckoutResponseDto from(
            final CheckoutResult result
    ) {
        return new CheckoutResponseDto(
                new ProductDto(
                        result.product().id(),
                        result.product().name(),
                        result.product().price(),
                        result.product().checkinAt(),
                        result.product().checkoutAt(),
                        result.product().onSale()
                ),
                new UserPointDto(result.userPoint().balance()),
                new StockDto(result.stock().remaining())
        );
    }

    public record ProductDto(
            Long id,
            String name,
            long price,
            String checkinAt,
            String checkoutAt,
            boolean onSale
    ) {}

    public record UserPointDto(long balance) {}

    public record StockDto(int remaining) {}
}
