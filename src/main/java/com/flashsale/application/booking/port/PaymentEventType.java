package com.flashsale.application.booking.port;

public enum PaymentEventType {
    CHARGE_REQUESTED,
    CHARGE_APPROVED,
    CHARGE_DECLINED,
    CHARGE_UNCERTAIN,
    CANCEL_REQUESTED,
    CANCEL_APPROVED,
    CANCEL_DECLINED,
    CANCEL_UNCERTAIN
}
