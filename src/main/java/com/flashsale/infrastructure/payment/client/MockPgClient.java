package com.flashsale.infrastructure.payment.client;

import com.flashsale.application.booking.dto.PaymentResult;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.UUID;

@Component
public class MockPgClient {

    @Value("${pg.mock.success-rate:0.95}")
    private double successRate;

    @Value("${pg.mock.failure-rate:0.03}")
    private double failureRate;

    @Value("${pg.mock.seed:-1}")
    private long seed;

    private Random random;

    @PostConstruct
    void init() {
        if (successRate + failureRate > 1.0) {
            throw new IllegalStateException(
                "pg.mock.success-rate + pg.mock.failure-rate must not exceed 1.0, got: "
                + (successRate + failureRate));
        }
        this.random = seed >= 0 ? new Random(seed) : new Random();
    }

    public PaymentResult charge(
            final String idempotencyKey
    ) {
        double roll = random.nextDouble();
        if (roll < successRate) {
            return new PaymentResult.Success(UUID.randomUUID().toString());
        } else if (roll < successRate + failureRate) {
            return new PaymentResult.Failure("LIMIT_EXCEEDED", "카드 한도 초과");
        } else {
            return new PaymentResult.Unknown(idempotencyKey);
        }
    }

    public PaymentResult cancel(
            final String pgTxId
    ) {
        double roll = random.nextDouble();
        if (roll < successRate) {
            return new PaymentResult.Success(pgTxId);
        } else if (roll < successRate + failureRate) {
            return new PaymentResult.Failure("CANCEL_DENIED", "취소 거절");
        } else {
            return new PaymentResult.Unknown(pgTxId);
        }
    }

    public PaymentResult inquiry(
            final String idempotencyKey
    ) {
        return new PaymentResult.Success(UUID.randomUUID().toString());
    }
}
