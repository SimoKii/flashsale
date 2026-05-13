package com.flashsale.domain.order;

import com.flashsale.common.exception.DomainException;
import com.flashsale.domain.shared.Money;

import java.util.List;

public class PaymentComposition {

    private PaymentComposition() {}

    public static void validate(
            final List<PaymentLine> lines,
            final Money totalAmount
    ) {
        if (lines == null || lines.isEmpty()) {
            throw new DomainException("Payment lines must not be empty");
        }

        final long pointCount = lines.stream().filter(l -> l.getMethod().isPoint()).count();
        final long externalCount = lines.stream().filter(l -> l.getMethod().isExternal()).count();
        final boolean hasCard = lines.stream().anyMatch(l -> l.getMethod() == PaymentMethodCode.CREDIT_CARD);
        final boolean hasYPay = lines.stream().anyMatch(l -> l.getMethod() == PaymentMethodCode.YPAY);

        if (hasCard && hasYPay) {
            throw new DomainException("CREDIT_CARD and YPAY cannot be combined");
        }
        if (pointCount > 1) {
            throw new DomainException("At most one YPOINT line is allowed");
        }
        if (externalCount > 1) {
            throw new DomainException("At most one external payment line is allowed");
        }

        final Money sum = lines.stream()
                .map(PaymentLine::getAmount)
                .reduce(Money.ZERO, Money::plus);

        if (!sum.equals(totalAmount)) {
            throw new DomainException("Payment line sum " + sum.amount() + " != totalAmount " + totalAmount.amount());
        }
    }
}
