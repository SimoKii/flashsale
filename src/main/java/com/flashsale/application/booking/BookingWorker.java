package com.flashsale.application.booking;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.application.booking.dto.PaymentLineCommand;
import com.flashsale.application.booking.port.BookingQueuePort;
import com.flashsale.application.booking.port.BookingQueuePort.QueueMessage;
import com.flashsale.application.booking.port.BookingResultStore;
import com.flashsale.application.booking.port.KillSwitchPort;
import com.flashsale.application.booking.port.OrderRepository;
import com.flashsale.application.booking.port.PaymentLineRepository;
import com.flashsale.application.booking.port.ProductRepository;
import com.flashsale.application.booking.port.StockPort;
import com.flashsale.application.booking.port.UserEntryGuardPort;
import com.flashsale.domain.order.IdempotencyKey;
import com.flashsale.domain.order.Order;
import com.flashsale.domain.order.PaymentLine;
import com.flashsale.domain.shared.Money;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class BookingWorker {

    private static final String CONSUMER_GROUP = "booking-worker";
    private static final int BATCH_SIZE = 10;
    private static final int MAX_DELIVERY = 3;
    private static final Duration CLAIM_IDLE = Duration.ofSeconds(30);
    private static final Duration RESULT_TTL = Duration.ofHours(24);

    private final BookingQueuePort bookingQueuePort;
    private final KillSwitchPort killSwitchPort;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final StockPort stockPort;
    private final PaymentLineRepository paymentLineRepository;
    private final PaymentOrchestrator paymentOrchestrator;
    private final BookingResultStore bookingResultStore;
    private final UserEntryGuardPort userEntryGuardPort;
    private final ObjectMapper objectMapper;

    public BookingWorker(
            final BookingQueuePort bookingQueuePort,
            final KillSwitchPort killSwitchPort,
            final OrderRepository orderRepository,
            final ProductRepository productRepository,
            @Qualifier("redisStockAdapter") final StockPort stockPort,
            final PaymentLineRepository paymentLineRepository,
            final PaymentOrchestrator paymentOrchestrator,
            final BookingResultStore bookingResultStore,
            final UserEntryGuardPort userEntryGuardPort,
            final ObjectMapper objectMapper
    ) {
        this.bookingQueuePort = bookingQueuePort;
        this.killSwitchPort = killSwitchPort;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.stockPort = stockPort;
        this.paymentLineRepository = paymentLineRepository;
        this.paymentOrchestrator = paymentOrchestrator;
        this.bookingResultStore = bookingResultStore;
        this.userEntryGuardPort = userEntryGuardPort;
        this.objectMapper = objectMapper;
    }

    @Value("${flashsale.instance-id:default}")
    private String instanceId;

    @Value("${flashsale.booking-worker.product-ids:1}")
    private String productIdsCsv;

    @Scheduled(fixedDelay = 500)
    public void poll() {
        List<Long> productIds = Arrays.stream(productIdsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .toList();

        String consumer = CONSUMER_GROUP + "-" + instanceId;

        for (Long productId : productIds) {
            try {
                List<QueueMessage> claimedMessages = bookingQueuePort.claim(
                        productId, CONSUMER_GROUP, consumer, CLAIM_IDLE, BATCH_SIZE);
                for (QueueMessage message : claimedMessages) {
                    processMessage(productId, message);
                }

                List<QueueMessage> messages = bookingQueuePort.read(
                        productId, CONSUMER_GROUP, consumer, BATCH_SIZE);
                for (QueueMessage message : messages) {
                    processMessage(productId, message);
                }
            } catch (Exception e) {
                log.error("Error polling product {}: {}", productId, e.getMessage(), e);
            }
        }
    }

    void processMessage(Long productId, QueueMessage message) {
        Map<String, String> fields = message.fields();
        String messageId = message.id();
        long deliveryCount = message.deliveryCount();

        String ticketId = messageId;
        Long userId = null;

        try {
            userId = Long.parseLong(fields.get("userId"));
            String idempotencyKey = fields.get("idempotencyKey");
            long totalAmount = Long.parseLong(fields.get("totalAmount"));

            if (killSwitchPort.isOn(productId)) {
                log.warn("Kill switch ON for productId={}, sending message {} to DLQ", productId, messageId);
                bookingQueuePort.sendToDlq(productId, fields);
                bookingQueuePort.ack(productId, CONSUMER_GROUP, messageId);
                return;
            }

            if (deliveryCount > MAX_DELIVERY) {
                log.warn("Max delivery exceeded for message {} productId={}, sending to DLQ", messageId, productId);
                bookingQueuePort.sendToDlq(productId, fields);
                bookingQueuePort.ack(productId, CONSUMER_GROUP, messageId);
                return;
            }

            final Long finalUserId = userId;

            Optional<Order> existingOrder = orderRepository.findByIdempotencyKey(idempotencyKey);
            if (existingOrder.isPresent()) {
                Order order = existingOrder.get();
                switch (order.getStatus()) {
                    case PAID -> {
                        String body = objectMapper.writeValueAsString(Map.of("orderId", order.getId()));
                        bookingResultStore.save(productId, ticketId, body, RESULT_TTL);
                        userEntryGuardPort.release(productId, finalUserId);
                    }
                    case FAILED, CANCELED -> {
                        bookingResultStore.save(productId, ticketId, "FAILED:ALREADY_FAILED:주문이 이미 실패 처리됨", RESULT_TTL);
                        userEntryGuardPort.release(productId, finalUserId);
                    }
                    default -> {
                        bookingResultStore.save(productId, ticketId, "UNCERTAIN:처리 중인 주문", RESULT_TTL);
                    }
                }
                bookingQueuePort.ack(productId, CONSUMER_GROUP, messageId);
                return;
            }

            var productOpt = productRepository.findById(productId);
            if (productOpt.isEmpty()) {
                log.warn("Product not found for productId={}", productId);
                bookingResultStore.save(productId, ticketId, "FAILED:PRODUCT_NOT_FOUND:상품을 찾을 수 없습니다", RESULT_TTL);
                userEntryGuardPort.release(productId, finalUserId);
                bookingQueuePort.ack(productId, CONSUMER_GROUP, messageId);
                return;
            }

            int remaining = stockPort.reserve(productId, ticketId, userId);
            if (remaining == -1) {
                log.warn("Stock exhausted for productId={}", productId);
                bookingResultStore.save(productId, ticketId, "FAILED:SOLD_OUT:재고가 소진되었습니다", RESULT_TTL);
                userEntryGuardPort.release(productId, finalUserId);
                bookingQueuePort.ack(productId, CONSUMER_GROUP, messageId);
                return;
            }

            Order order = Order.create(
                    IdempotencyKey.of(idempotencyKey),
                    userId,
                    productId,
                    Money.of(totalAmount),
                    LocalDateTime.now().plusMinutes(30)
            );
            Order savedOrder = orderRepository.save(order);

            List<PaymentLineCommand> commands = parsePaymentLines(fields.get("paymentLines"));
            for (PaymentLineCommand cmd : commands) {
                PaymentLine line = PaymentLine.of(cmd.method(), Money.of(cmd.amount()));
                PaymentLine saved = paymentLineRepository.save(line, savedOrder.getId());
                savedOrder.addPaymentLine(saved);
            }

            OrchestratorResult result = paymentOrchestrator.execute(savedOrder, commands);

            if (result instanceof OrchestratorResult.AllPaid) {
                String body = objectMapper.writeValueAsString(Map.of("orderId", savedOrder.getId()));
                savedOrder.markPaid(body);
                orderRepository.save(savedOrder);
                stockPort.confirm(productId, ticketId);
                bookingResultStore.save(productId, ticketId, body, RESULT_TTL);
                userEntryGuardPort.release(productId, finalUserId);
            } else if (result instanceof OrchestratorResult.Failed) {
                savedOrder.markFailed("FAILED");
                orderRepository.save(savedOrder);
                stockPort.restore(productId, ticketId, userId);
                bookingResultStore.save(productId, ticketId, "FAILED:PAYMENT_FAILED:결제에 실패했습니다", RESULT_TTL);
                userEntryGuardPort.release(productId, finalUserId);
            } else if (result instanceof OrchestratorResult.Compensating) {
                savedOrder.markCompensating();
                orderRepository.save(savedOrder);
                stockPort.restore(productId, ticketId, userId);
                bookingResultStore.save(productId, ticketId, "UNCERTAIN:결제 보상 처리 중", RESULT_TTL);
                userEntryGuardPort.release(productId, finalUserId);
            } else if (result instanceof OrchestratorResult.Uncertain) {
                savedOrder.markUncertain("UNCERTAIN");
                orderRepository.save(savedOrder);
                bookingResultStore.save(productId, ticketId, "UNCERTAIN:결제 결과 확인 중", RESULT_TTL);
            } else {
                log.error("Unknown OrchestratorResult type: {}", result.getClass().getName());
            }

            bookingQueuePort.ack(productId, CONSUMER_GROUP, messageId);

        } catch (Exception e) {
            log.error("Unexpected error processing message {} for productId={}: {}",
                    messageId, productId, e.getMessage(), e);
        }
    }

    private List<PaymentLineCommand> parsePaymentLines(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<PaymentLineCommand>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse paymentLines: " + json, e);
        }
    }
}
