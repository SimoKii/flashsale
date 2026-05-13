package com.flashsale.infrastructure.jpa.point.impl;

import com.flashsale.application.booking.port.PointRepository;
import com.flashsale.domain.point.PointAccount;
import com.flashsale.domain.point.PointTx;
import com.flashsale.infrastructure.jpa.point.PointAccountJpaEntity;
import com.flashsale.infrastructure.jpa.point.PointJpaRepository;
import com.flashsale.infrastructure.jpa.point.PointTxJpaEntity;
import com.flashsale.infrastructure.jpa.point.PointTxJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PointRepositoryImpl implements PointRepository {

    private final PointJpaRepository pointJpaRepository;
    private final PointTxJpaRepository pointTxJpaRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<PointAccount> findByUserId(
            final Long userId
    ) {
        return pointJpaRepository.findByUserId(userId)
                .map(PointAccountJpaEntity::toDomain);
    }

    @Override
    @Transactional
    public PointAccount save(
            final PointAccount account
    ) {
        return pointJpaRepository.findByUserId(account.getUserId())
                .map(entity -> {
                    entity.updateFrom(account);
                    return pointJpaRepository.saveAndFlush(entity).toDomain();
                })
                .orElseGet(() ->
                        pointJpaRepository.save(PointAccountJpaEntity.from(account)).toDomain()
                );
    }

    @Override
    @Transactional
    public PointTx saveTx(
            final PointTx tx
    ) {
        return pointTxJpaRepository.save(PointTxJpaEntity.from(tx)).toDomain();
    }
}