package com.flashsale.infrastructure.jpa.product.impl;

import com.flashsale.application.booking.port.ProductRepository;
import com.flashsale.domain.product.Product;
import com.flashsale.infrastructure.jpa.product.ProductJpaEntity;
import com.flashsale.infrastructure.jpa.product.ProductJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository jpaRepository;

    @Override
    public Optional<Product> findById(
            final Long id
    ) {
        return jpaRepository.findById(id).map(ProductJpaEntity::toDomain);
    }
}
