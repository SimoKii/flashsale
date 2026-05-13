package com.flashsale.infrastructure.jpa.stock.impl;

import com.flashsale.application.booking.port.StockPort;
import com.flashsale.common.exception.DomainException;
import com.flashsale.domain.stock.Stock;
import com.flashsale.infrastructure.jpa.stock.StockJpaEntity;
import com.flashsale.infrastructure.jpa.stock.StockJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class DatabaseStockAdapter implements StockPort {

    private final StockJpaRepository jpaRepository;

    @Override
    @Transactional
    public int reserve(
            final Long productId,
            final String ticketId,
            final Long userId
    ) {
        StockJpaEntity entity = findWithLock(productId);
        Stock stock = entity.toDomain();
        if (!stock.canReserve()) {
            return -1;
        }
        stock.reserve();
        entity.updateFrom(stock);
        return stock.remaining();
    }

    @Override
    @Transactional
    public void restore(
            final Long productId,
            final String ticketId,
            final Long userId
    ) {
        StockJpaEntity entity = findWithLock(productId);
        Stock stock = entity.toDomain();
        stock.restore();
        entity.updateFrom(stock);
    }

    @Override
    @Transactional
    public void confirm(
            final Long productId,
            final String ticketId
    ) {
        StockJpaEntity entity = findWithLock(productId);
        Stock stock = entity.toDomain();
        stock.confirm();
        entity.updateFrom(stock);
    }

    @Override
    @Transactional(readOnly = true)
    public int remaining(
            final Long productId
    ) {
        return findEntity(productId, false).toDomain().remaining();
    }

    private StockJpaEntity findWithLock(
            final Long productId
    ) {
        return findEntity(productId, true);
    }

    private StockJpaEntity findEntity(
            final Long productId,
            final boolean withLock
    ) {
        return (withLock
                ? jpaRepository.findByProductIdWithLock(productId)
                : jpaRepository.findByProductId(productId))
                .orElseThrow(() -> new DomainException("Stock not found for product: " + productId));
    }
}
