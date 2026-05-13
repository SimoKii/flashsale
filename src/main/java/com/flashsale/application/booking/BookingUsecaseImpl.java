package com.flashsale.application.booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.application.booking.dto.BookingAcceptedResult;
import com.flashsale.application.booking.dto.BookingCommand;
import com.flashsale.application.booking.dto.BookingStatusResult;
import com.flashsale.application.booking.exception.DuplicateBookingException;
import com.flashsale.application.booking.exception.InvalidPaymentCompositionException;
import com.flashsale.application.booking.port.BookingQueuePort;
import com.flashsale.application.booking.port.BookingResultStore;
import com.flashsale.application.booking.port.IdempotencyStore;
import com.flashsale.application.booking.port.UserEntryGuardPort;
import com.flashsale.common.exception.DomainException;
import com.flashsale.domain.order.PaymentComposition;
import com.flashsale.domain.order.PaymentLine;
import com.flashsale.domain.shared.Money;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingUsecaseImpl implements BookingUsecase {

    private final IdempotencyStore idempotencyStore;
    private final UserEntryGuardPort userEntryGuardPort;
    private final BookingQueuePort bookingQueuePort;
    private final BookingResultStore bookingResultStore;
    private final ObjectMapper objectMapper;

    private static final Duration IDEMPOTENCY_TTL = Duration.ofSeconds(600);

    @Override
    public BookingAcceptedResult book(
            final BookingCommand cmd
    ) {
        boolean acquired = idempotencyStore.setIfAbsent(cmd.idempotencyKey(), "PENDING", IDEMPOTENCY_TTL);
        if (!acquired) {
            String cached = idempotencyStore.get(cmd.idempotencyKey()).orElse(null);
            if (cached != null && !"PENDING".equals(cached)) {
                return new BookingAcceptedResult(cached, 0);
            }
            throw new DuplicateBookingException("Booking already in progress: " + cmd.idempotencyKey());
        }

        String ticketId = null;
        try {
            try {
                List<PaymentLine> lines = cmd.paymentLines().stream()
                        .map(lc -> PaymentLine.of(
                                lc.method(),
                                Money.of(lc.amount())
                        ))
                        .toList();
                PaymentComposition.validate(lines, Money.of(cmd.totalAmount()));
            } catch (DomainException e) {
                throw new InvalidPaymentCompositionException(e.getMessage());
            }

            boolean guardAcquired = userEntryGuardPort.acquire(cmd.productId(), cmd.userId(), IDEMPOTENCY_TTL);
            if (!guardAcquired) {
                throw new DuplicateBookingException("Duplicate booking for user: " + cmd.userId());
            }

            try {
                Map<String, String> fields = buildFields(cmd);
                ticketId = bookingQueuePort.enqueue(cmd.productId(), fields);
            } catch (Exception e) {
                userEntryGuardPort.release(cmd.productId(), cmd.userId());
                throw e;
            }
        } catch (Exception e) {
            if (ticketId == null) {
                idempotencyStore.update(cmd.idempotencyKey(), "INVALID", Duration.ofSeconds(5));
            }
            throw e;
        }

        idempotencyStore.update(cmd.idempotencyKey(), ticketId, IDEMPOTENCY_TTL);

        return new BookingAcceptedResult(ticketId, 0);
    }

    @Override
    public BookingStatusResult getBookingStatus(
            final Long productId,
            final String ticketId
    ) {
        return bookingResultStore.find(productId, ticketId)
                .map(this::parseStatus)
                .orElse(new BookingStatusResult.Pending());
    }

    private Map<String, String> buildFields(
            final BookingCommand cmd
    ) {
        try {
            return Map.of(
                    "productId", cmd.productId().toString(),
                    "userId", cmd.userId().toString(),
                    "idempotencyKey", cmd.idempotencyKey(),
                    "totalAmount", String.valueOf(cmd.totalAmount()),
                    "paymentLines", objectMapper.writeValueAsString(cmd.paymentLines())
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize booking fields", e);
        }
    }

    private BookingStatusResult parseStatus(
            final String body
    ) {
        if ("PENDING".equals(body)) return new BookingStatusResult.Pending();
        if (body.startsWith("FAILED:")) {
            String[] parts = body.substring(7).split(":", 2);
            return new BookingStatusResult.Failed(parts[0], parts.length > 1 ? parts[1] : "");
        }
        if (body.startsWith("UNCERTAIN:")) {
            return new BookingStatusResult.Uncertain(body.substring(10));
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            JsonNode orderIdNode = node.path("orderId");
            if (!orderIdNode.isMissingNode() && orderIdNode.isNumber()) {
                return new BookingStatusResult.Paid(orderIdNode.asLong(), body);
            }
            log.warn("Cannot parse booking result body: {}", body);
            return new BookingStatusResult.Uncertain("Unparseable result");
        } catch (Exception e) {
            log.warn("Cannot parse booking result body: {}", body);
            return new BookingStatusResult.Uncertain("Unparseable result");
        }
    }
}
