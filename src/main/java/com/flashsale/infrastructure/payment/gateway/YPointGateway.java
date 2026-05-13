package com.flashsale.infrastructure.payment.gateway;

import com.flashsale.application.booking.dto.PaymentLineCommand;
import com.flashsale.application.booking.dto.PaymentResult;
import com.flashsale.application.booking.port.PaymentGateway;
import com.flashsale.application.point.PointService;
import com.flashsale.domain.order.PaymentMethodCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class YPointGateway implements PaymentGateway {

    private final PointService pointService;

    @Override
    public PaymentMethodCode supports() {
        return PaymentMethodCode.YPOINT;
    }

    @Override
    public PaymentResult charge(
            final PaymentLineCommand cmd
    ) {
        try {
            pointService.deduct(
                    cmd.userId(),
                    cmd.amount(),
                    cmd.idempotencyKey()
            );
            String pgTxId = "point:" + cmd.userId() + ":" + cmd.amount();
            return new PaymentResult.Success(pgTxId);
        } catch (Exception e) {
            log.warn("Point deduction failed: {}", e.getMessage());
            return new PaymentResult.Failure("POINT_INSUFFICIENT", e.getMessage());
        }
    }

    @Override
    public PaymentResult cancel(
            final String pgTxId
    ) {
        try {
            String[] parts = pgTxId.split(":");
            if (parts.length < 3) {
                return new PaymentResult.Failure("INVALID_TX_ID", "Invalid pgTxId format: " + pgTxId);
            }
            long userId = Long.parseLong(parts[1]);
            long amount = Long.parseLong(parts[2]);
            pointService.refund(userId, amount, pgTxId);
            return new PaymentResult.Success(pgTxId);
        } catch (NumberFormatException e) {
            log.warn("Point refund failed — malformed pgTxId {}: {}", pgTxId, e.getMessage());
            return new PaymentResult.Failure("INVALID_TX_ID", "Malformed pgTxId: " + pgTxId);
        } catch (Exception e) {
            log.warn("Point refund failed: {}", e.getMessage());
            return new PaymentResult.Failure("POINT_REFUND_FAILED", e.getMessage());
        }
    }

    @Override
    public PaymentResult inquiry(
            final String idempotencyKey
    ) {
        return new PaymentResult.Success(idempotencyKey);
    }


}
