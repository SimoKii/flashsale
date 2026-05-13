package com.flashsale.application.booking.port;

import com.flashsale.application.booking.dto.PaymentLineCommand;
import com.flashsale.application.booking.dto.PaymentResult;
import com.flashsale.domain.order.PaymentMethodCode;

public interface PaymentGateway {

    PaymentMethodCode supports();

    PaymentResult charge(PaymentLineCommand cmd);

    PaymentResult cancel(String pgTxId);

    PaymentResult inquiry(String idempotencyKey);
}
