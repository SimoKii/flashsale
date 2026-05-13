package com.flashsale.domain.stock;

import com.flashsale.common.exception.DomainException;

public class StockExhaustedException extends DomainException {

    public StockExhaustedException(final long productId) {
        super("Stock exhausted for product: " + productId);
    }
}
