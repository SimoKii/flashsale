package com.flashsale.domain.order;

public enum PaymentMethodCode {
    CREDIT_CARD, YPAY, YPOINT;

    public boolean isExternal() { return this == CREDIT_CARD || this == YPAY; }
    public boolean isPoint() { return this == YPOINT; }
}
