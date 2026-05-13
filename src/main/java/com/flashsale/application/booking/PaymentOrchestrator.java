package com.flashsale.application.booking;

import com.flashsale.application.booking.dto.PaymentLineCommand;
import com.flashsale.domain.order.Order;

import java.util.List;

public interface PaymentOrchestrator {
    OrchestratorResult execute(
            Order order,
            List<PaymentLineCommand> lineCommands
    );
}
