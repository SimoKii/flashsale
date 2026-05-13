package com.flashsale.application.checkout.dto;

import java.util.Objects;

public record CheckoutQuery(
        Long productId,
        Long userId
) {
    public CheckoutQuery {
        Objects.requireNonNull(productId, "productId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
    }
}
