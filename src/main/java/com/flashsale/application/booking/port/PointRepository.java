package com.flashsale.application.booking.port;

import com.flashsale.domain.point.PointAccount;
import com.flashsale.domain.point.PointTx;

import java.util.Optional;

public interface PointRepository {

    Optional<PointAccount> findByUserId(final Long userId);

    PointAccount save(final PointAccount account);

    PointTx saveTx(final PointTx tx);

    boolean existsByIdempotencyKey(final String idempotencyKey);
}
