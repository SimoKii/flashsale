package com.flashsale.application.booking;

import com.flashsale.application.booking.dto.PaymentLineCommand;
import com.flashsale.application.booking.dto.PaymentResult;
import com.flashsale.application.booking.port.PaymentEventRepository;
import com.flashsale.application.booking.port.PaymentEventType;
import com.flashsale.application.booking.port.PaymentGateway;
import com.flashsale.application.booking.port.PaymentReconcileQueue;
import com.flashsale.domain.order.Order;
import com.flashsale.domain.order.PaymentLine;
import com.flashsale.domain.order.PaymentMethodCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.flashsale.application.booking.dto.PaymentResult.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentOrchestratorImpl implements PaymentOrchestrator {

    private final List<PaymentGateway> gateways;
    private final PaymentEventRepository eventRepository;
    private final PaymentReconcileQueue reconcileQueue;

    @Override
    public OrchestratorResult execute(
            final Order order,
            final List<PaymentLineCommand> lineCommands
    ) {
        List<PaymentLineCommand> sorted = lineCommands.stream()
                .sorted(Comparator.comparingInt(PaymentLineCommand::sequence))
                .toList();

        List<PaymentLine> approvedLines = new ArrayList<>();

        for (PaymentLineCommand cmd : sorted) {
            PaymentLine line = order.getPaymentLines().stream()
                    .filter(l -> l.getSequence() == cmd.sequence())
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("PaymentLine not found for sequence: " + cmd.sequence()));

            PaymentGateway gateway = findGateway(cmd.method());

            saveEvent(line.getId(), PaymentEventType.CHARGE_REQUESTED, null, cmd.idempotencyKey());

            PaymentResult result;
            try {
                result = gateway.charge(cmd);
            } catch (Exception e) {
                order.uncertainPaymentLine(cmd.sequence());
                saveEvent(line.getId(), PaymentEventType.CHARGE_UNCERTAIN, null, null);
                return new OrchestratorResult.Uncertain();
            }

            if (result instanceof Success success) {
                order.approvePaymentLine(cmd.sequence(), success.pgTxId());
                saveEvent(line.getId(), PaymentEventType.CHARGE_APPROVED, success.pgTxId(), null);

                PaymentLine updatedLine = order.getPaymentLines().stream()
                        .filter(l -> l.getSequence() == cmd.sequence())
                        .findFirst()
                        .orElseThrow();
                approvedLines.add(updatedLine);

            } else if (result instanceof Failure failure) {
                order.declinePaymentLine(cmd.sequence());
                saveEvent(line.getId(), PaymentEventType.CHARGE_DECLINED, null, failure.reasonCode());
                return compensate(order, approvedLines);

            } else if (result instanceof Unknown unknown) {
                order.uncertainPaymentLine(cmd.sequence());
                saveEvent(line.getId(), PaymentEventType.CHARGE_UNCERTAIN, null, null);
                reconcileQueue.enqueue(
                        PaymentReconcileQueue.ReconcileType.PAYMENT_INQUIRY,
                        order.getId(),
                        line.getId(),
                        unknown.idempotencyKey()
                );
                return new OrchestratorResult.Uncertain();
            }
        }

        return new OrchestratorResult.AllPaid();
    }

    private OrchestratorResult compensate(
            final Order order,
            final List<PaymentLine> approvedLines
    ) {
        boolean hasUnresolved = false;

        List<PaymentLine> reversed = new ArrayList<>(approvedLines);
        Collections.reverse(reversed);

        for (PaymentLine line : reversed) {
            saveEvent(line.getId(), PaymentEventType.CANCEL_REQUESTED, line.getPgTxId(), null);

            PaymentResult cancelResult;
            try {
                cancelResult = findGateway(line.getMethod()).cancel(line.getPgTxId());
            } catch (Exception e) {
                order.pendCancelPaymentLine(line.getSequence());
                enqueueReconcile(order.getId(), line.getId());
                hasUnresolved = true;
                continue;
            }

            if (cancelResult instanceof Success) {
                order.cancelPaymentLine(line.getSequence());
                saveEvent(line.getId(), PaymentEventType.CANCEL_APPROVED, null, null);

            } else if (cancelResult instanceof Failure failure) {
                order.pendCancelPaymentLine(line.getSequence());
                order.failCancelPaymentLine(line.getSequence());
                saveEvent(line.getId(), PaymentEventType.CANCEL_DECLINED, null, failure.reasonCode());
                enqueueReconcile(order.getId(), line.getId());
                hasUnresolved = true;

            } else if (cancelResult instanceof Unknown unknown) {
                order.pendCancelPaymentLine(line.getSequence());
                saveEvent(line.getId(), PaymentEventType.CANCEL_UNCERTAIN, null, null);
                reconcileQueue.enqueue(
                        PaymentReconcileQueue.ReconcileType.CANCEL_RETRY,
                        order.getId(),
                        line.getId(),
                        unknown.idempotencyKey()
                );
                hasUnresolved = true;
            }
        }

        return hasUnresolved ? new OrchestratorResult.Compensating() : new OrchestratorResult.Failed();
    }

    private PaymentGateway findGateway(
            final PaymentMethodCode method
    ) {
        return gateways.stream()
                .filter(g -> g.supports() == method)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No gateway found for method: " + method));
    }

    private void saveEvent(
            final Long paymentLineId,
            final PaymentEventType type,
            final String pgTxId,
            final String responseBody
    ) {
        try {
            eventRepository.save(
                    paymentLineId,
                    type,
                    pgTxId,
                    responseBody
            );
        } catch (Exception e) {
            log.warn("Failed to save payment event [lineId={}, type={}]: {}", paymentLineId, type, e.getMessage());
        }
    }

    private void enqueueReconcile(
            final Long orderId,
            final Long paymentLineId
    ) {
        reconcileQueue.enqueue(
                PaymentReconcileQueue.ReconcileType.CANCEL_RETRY,
                orderId,
                paymentLineId,
                "cancel-" + paymentLineId
        );
    }
}
