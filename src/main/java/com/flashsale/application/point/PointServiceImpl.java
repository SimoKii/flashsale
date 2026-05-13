package com.flashsale.application.point;

import com.flashsale.application.booking.port.PointRepository;
import com.flashsale.common.exception.DomainException;
import com.flashsale.domain.point.PointAccount;
import com.flashsale.domain.point.PointTx;
import com.flashsale.domain.shared.Money;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PointServiceImpl implements PointService {

    private final PointRepository pointRepository;

    @Override
    @Transactional
    public void deduct(
            final long userId,
            final long amount,
            final String idempotencyKey
    ) {
        if (idempotencyKey != null && pointRepository.existsByIdempotencyKey(idempotencyKey)) {
            log.info("Duplicate point deduction ignored — idempotencyKey={}", idempotencyKey);
            return;
        }
        PointAccount account = pointRepository.findByUserId(userId)
                .orElseThrow(() -> new DomainException("PointAccount not found: " + userId));
        account.deduct(Money.of(amount));
        pointRepository.save(account);
        pointRepository.saveTx(PointTx.of(
                userId,
                null,
                PointTx.Type.USE,
                Money.of(amount),
                idempotencyKey
        ));
    }

    @Override
    @Transactional
    public void refund(
            final long userId,
            final long amount,
            final String idempotencyKey
    ) {
        PointAccount account = pointRepository.findByUserId(userId)
                .orElseThrow(() -> new DomainException("PointAccount not found: " + userId));
        account.refund(Money.of(amount));
        pointRepository.save(account);
        pointRepository.saveTx(
                PointTx.of(
                        userId,
                        null,
                        PointTx.Type.REFUND,
                        Money.of(amount)
                )
        );
    }

    @Override
    @Transactional(readOnly = true)
    public long findBalance(
            final long userId
    ) {
        return pointRepository.findByUserId(userId)
                .map(PointAccount::getBalance)
                .orElse(0L);
    }
}
