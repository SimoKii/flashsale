package com.flashsale.infrastructure.payment.gateway;

import com.flashsale.application.booking.dto.PaymentLineCommand;
import com.flashsale.application.booking.dto.PaymentResult;
import com.flashsale.application.booking.port.PaymentGateway;
import com.flashsale.domain.order.PaymentMethodCode;
import com.flashsale.infrastructure.payment.client.MockPgClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CreditCardGateway implements PaymentGateway {

    private final MockPgClient mockPgClient;

    @Override
    public PaymentMethodCode supports() {
        return PaymentMethodCode.CREDIT_CARD;
    }

    @Override
    public PaymentResult charge(
            final PaymentLineCommand cmd
    ) {
        return mockPgClient.charge(cmd.idempotencyKey());
    }

    @Override
    public PaymentResult cancel(
            final String pgTxId
    ) {
        return mockPgClient.cancel(pgTxId);
    }

    @Override
    public PaymentResult inquiry(
            final String idempotencyKey
    ) {
        return mockPgClient.inquiry(idempotencyKey);
    }
}
