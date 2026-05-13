package com.flashsale.application.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.application.booking.port.BookingQueuePort;
import com.flashsale.application.booking.port.BookingResultStore;
import com.flashsale.application.booking.port.KillSwitchPort;
import com.flashsale.application.booking.port.OrderRepository;
import com.flashsale.application.booking.port.PaymentLineRepository;
import com.flashsale.application.booking.port.ProductRepository;
import com.flashsale.application.booking.port.StockPort;
import com.flashsale.application.booking.port.UserEntryGuardPort;
import com.flashsale.domain.order.IdempotencyKey;
import com.flashsale.domain.order.Order;
import com.flashsale.domain.order.OrderStatus;
import com.flashsale.domain.product.Product;
import com.flashsale.domain.shared.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookingWorker")
class BookingWorkerTest {

    @Mock
    private BookingQueuePort bookingQueuePort;

    @Mock
    private KillSwitchPort killSwitchPort;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StockPort stockPort;

    @Mock
    private PaymentLineRepository paymentLineRepository;

    @Mock
    private PaymentOrchestrator paymentOrchestrator;

    @Mock
    private BookingResultStore bookingResultStore;

    @Mock
    private UserEntryGuardPort userEntryGuardPort;

    private BookingWorker bookingWorker;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Long PRODUCT_ID = 1L;
    private static final Long USER_ID = 42L;
    private static final String TICKET_ID = "1234-0";
    private static final String IDEMPOTENCY_KEY = "test-idem-key";

    @BeforeEach
    void setUp() {
        bookingWorker = new BookingWorker(
                bookingQueuePort,
                killSwitchPort,
                orderRepository,
                productRepository,
                stockPort,
                paymentLineRepository,
                paymentOrchestrator,
                bookingResultStore,
                userEntryGuardPort,
                objectMapper
        );
        ReflectionTestUtils.setField(bookingWorker, "instanceId", "test");
        ReflectionTestUtils.setField(bookingWorker, "productIdsCsv", "1");
    }

    private BookingQueuePort.QueueMessage message(
            final long deliveryCount
    ) {
        Map<String, String> fields = new HashMap<>();
        fields.put("productId", String.valueOf(PRODUCT_ID));
        fields.put("userId", String.valueOf(USER_ID));
        fields.put("idempotencyKey", IDEMPOTENCY_KEY);
        fields.put("totalAmount", "50000");
        fields.put("paymentLines", "[{\"sequence\":1,\"method\":\"YPOINT\",\"amount\":50000,\"cardNumber\":null,\"idempotencyKey\":\"line-1\"}]");
        return new BookingQueuePort.QueueMessage(TICKET_ID, fields, deliveryCount);
    }

    private Product validProduct() {
        return Product.restore(
                PRODUCT_ID,
                "Test Product",
                Money.of(50_000L),
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().minusHours(1)
        );
    }

    private Order newPendingOrder() {
        Order order = Order.create(
                IdempotencyKey.of(IDEMPOTENCY_KEY),
                USER_ID,
                PRODUCT_ID,
                Money.of(50_000L),
                LocalDateTime.now().plusMinutes(30)
        );
        ReflectionTestUtils.setField(order, "id", 100L);
        return order;
    }

    private Order existingOrder(
            final OrderStatus status,
            final String responseBody
    ) {
        return Order.restore(
                100L,
                IdempotencyKey.of(IDEMPOTENCY_KEY),
                USER_ID,
                PRODUCT_ID,
                Money.of(50_000L),
                status,
                LocalDateTime.now().plusMinutes(30),
                responseBody,
                List.of(),
                LocalDateTime.now()
        );
    }

    private void mockNoExistingOrder() {
        when(orderRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
    }

    private void mockHappyPathThroughReserve(
            final int remaining
    ) {
        when(killSwitchPort.isOn(PRODUCT_ID)).thenReturn(false);
        mockNoExistingOrder();
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(validProduct()));
        when(stockPort.reserve(anyLong(), anyString(), anyLong())).thenReturn(remaining);
    }

    private void mockOrderAndPaymentLineSave() {
        Order order = newPendingOrder();
        when(orderRepository.save(any())).thenReturn(order);
        when(paymentLineRepository.save(any(), any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Nested
    @DisplayName("Kill Switch 차단")
    class KillSwitch {

        @Test
        @DisplayName("Kill Switch가 켜져 있으면 DLQ로 보내고 ACK한다")
        void killSwitchOn_sendsToDlqAndAcks() {
            when(killSwitchPort.isOn(PRODUCT_ID)).thenReturn(true);

            bookingWorker.processMessage(PRODUCT_ID, message(1));

            verify(bookingQueuePort).sendToDlq(eq(PRODUCT_ID), any());
            verify(bookingQueuePort).ack(eq(PRODUCT_ID), anyString(), eq(TICKET_ID));
        }

        @Test
        @DisplayName("Kill Switch가 켜져 있으면 주문 조회로 진행하지 않는다")
        void killSwitchOn_skipsOrderLookup() {
            when(killSwitchPort.isOn(PRODUCT_ID)).thenReturn(true);

            bookingWorker.processMessage(PRODUCT_ID, message(1));

            verify(orderRepository, never()).findByIdempotencyKey(anyString());
        }
    }

    @Nested
    @DisplayName("DLQ 임계값")
    class DeliveryLimit {

        @Test
        @DisplayName("delivery count가 4를 초과하면 DLQ로 보내고 ACK한다")
        void exceedsThreshold_sendsToDlqAndAcks() {
            when(killSwitchPort.isOn(PRODUCT_ID)).thenReturn(false);

            bookingWorker.processMessage(PRODUCT_ID, message(5));

            verify(bookingQueuePort).sendToDlq(eq(PRODUCT_ID), any());
            verify(bookingQueuePort).ack(eq(PRODUCT_ID), anyString(), eq(TICKET_ID));
        }

        @Test
        @DisplayName("delivery count가 임계값 초과 시 주문 조회로 진행하지 않는다")
        void exceedsThreshold_skipsOrderLookup() {
            when(killSwitchPort.isOn(PRODUCT_ID)).thenReturn(false);

            bookingWorker.processMessage(PRODUCT_ID, message(5));

            verify(orderRepository, never()).findByIdempotencyKey(anyString());
        }
    }

    @Nested
    @DisplayName("기존 주문 처리 (멱등성 재처리)")
    class ExistingOrder {

        @Test
        @DisplayName("이미 PAID 상태인 주문이면 캐시된 응답을 저장한다")
        void paidOrder_savesCachedResponse() {
            when(killSwitchPort.isOn(PRODUCT_ID)).thenReturn(false);
            when(orderRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                    .thenReturn(Optional.of(existingOrder(OrderStatus.PAID, "{\"orderId\":100}")));

            bookingWorker.processMessage(PRODUCT_ID, message(1));

            verify(bookingResultStore).save(
                    eq(PRODUCT_ID),
                    eq(TICKET_ID),
                    contains("orderId"),
                    any(Duration.class)
            );
        }

        @Test
        @DisplayName("이미 PAID 상태인 주문 처리 시 가드를 해제하고 ACK한다")
        void paidOrder_releasesGuardAndAcks() {
            when(killSwitchPort.isOn(PRODUCT_ID)).thenReturn(false);
            when(orderRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                    .thenReturn(Optional.of(existingOrder(OrderStatus.PAID, "{\"orderId\":100}")));

            bookingWorker.processMessage(PRODUCT_ID, message(1));

            verify(userEntryGuardPort).release(eq(PRODUCT_ID), eq(USER_ID));
            verify(bookingQueuePort).ack(eq(PRODUCT_ID), anyString(), eq(TICKET_ID));
        }

        @Test
        @DisplayName("이미 FAILED 상태인 주문이면 FAILED 응답을 저장한다")
        void failedOrder_savesFailedResponse() {
            when(killSwitchPort.isOn(PRODUCT_ID)).thenReturn(false);
            when(orderRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                    .thenReturn(Optional.of(existingOrder(OrderStatus.FAILED, "FAILED")));

            bookingWorker.processMessage(PRODUCT_ID, message(1));

            verify(bookingResultStore).save(
                    eq(PRODUCT_ID),
                    eq(TICKET_ID),
                    contains("FAILED:ALREADY_FAILED"),
                    any(Duration.class)
            );
            verify(userEntryGuardPort).release(eq(PRODUCT_ID), eq(USER_ID));
            verify(bookingQueuePort).ack(eq(PRODUCT_ID), anyString(), eq(TICKET_ID));
        }
    }

    @Nested
    @DisplayName("사전 검증 실패")
    class PreValidationFailure {

        @Test
        @DisplayName("상품을 찾을 수 없으면 FAILED 응답을 저장하고 가드를 해제한다")
        void productNotFound_savesFailedAndReleasesGuard() {
            when(killSwitchPort.isOn(PRODUCT_ID)).thenReturn(false);
            mockNoExistingOrder();
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

            bookingWorker.processMessage(PRODUCT_ID, message(1));

            verify(bookingResultStore).save(
                    eq(PRODUCT_ID),
                    eq(TICKET_ID),
                    contains("FAILED"),
                    any(Duration.class)
            );
            verify(userEntryGuardPort).release(eq(PRODUCT_ID), eq(USER_ID));
            verify(bookingQueuePort).ack(eq(PRODUCT_ID), anyString(), eq(TICKET_ID));
        }

        @Test
        @DisplayName("상품을 찾을 수 없을 때 재고는 복구하지 않는다")
        void productNotFound_doesNotRestoreStock() {
            when(killSwitchPort.isOn(PRODUCT_ID)).thenReturn(false);
            mockNoExistingOrder();
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

            bookingWorker.processMessage(PRODUCT_ID, message(1));

            verify(stockPort, never()).restore(anyLong(), anyString(), anyLong());
        }

        @Test
        @DisplayName("재고 점유에 실패하면 FAILED 응답을 저장하고 가드를 해제한다")
        void stockExhausted_savesFailedAndReleasesGuard() {
            when(killSwitchPort.isOn(PRODUCT_ID)).thenReturn(false);
            mockNoExistingOrder();
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(validProduct()));
            when(stockPort.reserve(anyLong(), anyString(), anyLong())).thenReturn(-1);

            bookingWorker.processMessage(PRODUCT_ID, message(1));

            verify(bookingResultStore).save(
                    eq(PRODUCT_ID),
                    eq(TICKET_ID),
                    contains("FAILED"),
                    any(Duration.class)
            );
            verify(userEntryGuardPort).release(eq(PRODUCT_ID), eq(USER_ID));
            verify(bookingQueuePort).ack(eq(PRODUCT_ID), anyString(), eq(TICKET_ID));
        }

        @Test
        @DisplayName("재고 점유에 실패하면 재고를 복구하지 않는다")
        void stockExhausted_doesNotRestoreStock() {
            when(killSwitchPort.isOn(PRODUCT_ID)).thenReturn(false);
            mockNoExistingOrder();
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(validProduct()));
            when(stockPort.reserve(anyLong(), anyString(), anyLong())).thenReturn(-1);

            bookingWorker.processMessage(PRODUCT_ID, message(1));

            verify(stockPort, never()).restore(anyLong(), anyString(), anyLong());
        }
    }

    @Nested
    @DisplayName("결제 성공")
    class PaymentSuccess {

        @Test
        @DisplayName("모든 결제가 성공하면 재고를 확정한다")
        void allPaid_confirmsStock() {
            mockHappyPathThroughReserve(5);
            mockOrderAndPaymentLineSave();
            when(paymentOrchestrator.execute(any(), any()))
                    .thenReturn(new OrchestratorResult.AllPaid());

            bookingWorker.processMessage(PRODUCT_ID, message(1));

            verify(stockPort).confirm(eq(PRODUCT_ID), eq(TICKET_ID));
        }

        @Test
        @DisplayName("모든 결제가 성공하면 응답에 주문 ID가 포함된다")
        void allPaid_savesOrderIdInResponse() {
            mockHappyPathThroughReserve(5);
            mockOrderAndPaymentLineSave();
            when(paymentOrchestrator.execute(any(), any()))
                    .thenReturn(new OrchestratorResult.AllPaid());

            bookingWorker.processMessage(PRODUCT_ID, message(1));

            verify(bookingResultStore).save(
                    eq(PRODUCT_ID),
                    eq(TICKET_ID),
                    contains("orderId"),
                    any(Duration.class)
            );
        }

        @Test
        @DisplayName("모든 결제가 성공하면 가드를 해제하고 ACK한다")
        void allPaid_releasesGuardAndAcks() {
            mockHappyPathThroughReserve(5);
            mockOrderAndPaymentLineSave();
            when(paymentOrchestrator.execute(any(), any()))
                    .thenReturn(new OrchestratorResult.AllPaid());

            bookingWorker.processMessage(PRODUCT_ID, message(1));

            verify(userEntryGuardPort).release(eq(PRODUCT_ID), eq(USER_ID));
            verify(bookingQueuePort).ack(eq(PRODUCT_ID), anyString(), eq(TICKET_ID));
        }
    }

    @Nested
    @DisplayName("결제 실패")
    class PaymentFailure {

        @Test
        @DisplayName("결제가 명시적으로 실패하면 FAILED 응답을 저장한다")
        void failed_savesFailedResponse() {
            mockHappyPathThroughReserve(5);
            mockOrderAndPaymentLineSave();
            when(paymentOrchestrator.execute(any(), any()))
                    .thenReturn(new OrchestratorResult.Failed());

            bookingWorker.processMessage(PRODUCT_ID, message(1));

            verify(bookingResultStore).save(
                    eq(PRODUCT_ID),
                    eq(TICKET_ID),
                    contains("FAILED:PAYMENT_FAILED"),
                    any(Duration.class)
            );
        }

        @Test
        @DisplayName("결제 실패 시 재고를 복구한다")
        void failed_restoresStock() {
            mockHappyPathThroughReserve(5);
            mockOrderAndPaymentLineSave();
            when(paymentOrchestrator.execute(any(), any()))
                    .thenReturn(new OrchestratorResult.Failed());

            bookingWorker.processMessage(PRODUCT_ID, message(1));

            verify(stockPort).restore(eq(PRODUCT_ID), eq(TICKET_ID), eq(USER_ID));
        }

        @Test
        @DisplayName("결제 실패 시 가드를 해제하고 ACK한다")
        void failed_releasesGuardAndAcks() {
            mockHappyPathThroughReserve(5);
            mockOrderAndPaymentLineSave();
            when(paymentOrchestrator.execute(any(), any()))
                    .thenReturn(new OrchestratorResult.Failed());

            bookingWorker.processMessage(PRODUCT_ID, message(1));

            verify(userEntryGuardPort).release(eq(PRODUCT_ID), eq(USER_ID));
            verify(bookingQueuePort).ack(eq(PRODUCT_ID), anyString(), eq(TICKET_ID));
        }
    }

    @Nested
    @DisplayName("결제 불확실")
    class PaymentUncertain {

        @Test
        @DisplayName("결제가 불확실하면 UNCERTAIN 응답을 저장한다")
        void uncertain_savesUncertainResponse() {
            mockHappyPathThroughReserve(5);
            mockOrderAndPaymentLineSave();
            when(paymentOrchestrator.execute(any(), any()))
                    .thenReturn(new OrchestratorResult.Uncertain());

            bookingWorker.processMessage(PRODUCT_ID, message(1));

            verify(bookingResultStore).save(
                    eq(PRODUCT_ID),
                    eq(TICKET_ID),
                    contains("UNCERTAIN"),
                    any(Duration.class)
            );
        }

        @Test
        @DisplayName("결제 불확실 시 재고를 복구하지 않는다")
        void uncertain_doesNotRestoreStock() {
            mockHappyPathThroughReserve(5);
            mockOrderAndPaymentLineSave();
            when(paymentOrchestrator.execute(any(), any()))
                    .thenReturn(new OrchestratorResult.Uncertain());

            bookingWorker.processMessage(PRODUCT_ID, message(1));

            verify(stockPort, never()).restore(anyLong(), anyString(), anyLong());
        }

        @Test
        @DisplayName("결제 불확실 시 가드를 해제하지 않는다")
        void uncertain_doesNotReleaseGuard() {
            mockHappyPathThroughReserve(5);
            mockOrderAndPaymentLineSave();
            when(paymentOrchestrator.execute(any(), any()))
                    .thenReturn(new OrchestratorResult.Uncertain());

            bookingWorker.processMessage(PRODUCT_ID, message(1));

            verify(userEntryGuardPort, never()).release(anyLong(), anyLong());
        }

        @Test
        @DisplayName("결제 불확실 시에도 ACK한다")
        void uncertain_acks() {
            mockHappyPathThroughReserve(5);
            mockOrderAndPaymentLineSave();
            when(paymentOrchestrator.execute(any(), any()))
                    .thenReturn(new OrchestratorResult.Uncertain());

            bookingWorker.processMessage(PRODUCT_ID, message(1));

            verify(bookingQueuePort).ack(eq(PRODUCT_ID), anyString(), eq(TICKET_ID));
        }
    }

    @Nested
    @DisplayName("결제 보상 진행 중")
    class PaymentCompensating {

        @Test
        @DisplayName("결제 보상 진행 중이면 UNCERTAIN 응답을 저장한다")
        void compensating_savesUncertainResponse() {
            mockHappyPathThroughReserve(5);
            mockOrderAndPaymentLineSave();
            when(paymentOrchestrator.execute(any(), any()))
                    .thenReturn(new OrchestratorResult.Compensating());

            bookingWorker.processMessage(PRODUCT_ID, message(1));

            verify(bookingResultStore).save(
                    eq(PRODUCT_ID),
                    eq(TICKET_ID),
                    contains("UNCERTAIN"),
                    any(Duration.class)
            );
        }

        @Test
        @DisplayName("결제 보상 진행 중이면 재고를 복구한다")
        void compensating_restoresStock() {
            mockHappyPathThroughReserve(5);
            mockOrderAndPaymentLineSave();
            when(paymentOrchestrator.execute(any(), any()))
                    .thenReturn(new OrchestratorResult.Compensating());

            bookingWorker.processMessage(PRODUCT_ID, message(1));

            verify(stockPort).restore(eq(PRODUCT_ID), eq(TICKET_ID), eq(USER_ID));
        }

        @Test
        @DisplayName("결제 보상 진행 중이면 가드를 해제하고 ACK한다")
        void compensating_releasesGuardAndAcks() {
            mockHappyPathThroughReserve(5);
            mockOrderAndPaymentLineSave();
            when(paymentOrchestrator.execute(any(), any()))
                    .thenReturn(new OrchestratorResult.Compensating());

            bookingWorker.processMessage(PRODUCT_ID, message(1));

            verify(userEntryGuardPort).release(eq(PRODUCT_ID), eq(USER_ID));
            verify(bookingQueuePort).ack(eq(PRODUCT_ID), anyString(), eq(TICKET_ID));
        }
    }
}