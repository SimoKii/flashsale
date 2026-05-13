package com.flashsale.application.booking.port;

public interface KillSwitchPort {

    boolean isOn(final Long productId);

    void turnOn(
            final Long productId,
            final String reason
    );

    void turnOff(final Long productId);
}
