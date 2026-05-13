package com.flashsale.application.booking.port;

import com.flashsale.domain.product.Product;

import java.util.Optional;

public interface ProductRepository {

    Optional<Product> findById(final Long id);
}
