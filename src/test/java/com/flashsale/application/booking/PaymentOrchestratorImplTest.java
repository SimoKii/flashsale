package com.flashsale.application.booking;

import com.flashsale.application.booking.dto.PaymentLineCommand;
import com.flashsale.application.booking.dto.PaymentResult;
import com.flashsale.application.booking.port.PaymentEventRepository;
import com.flashsale.application.booking.port.PaymentEventType;
import com.flashsale.application.booking.port.PaymentGateway;
import com.flashsale.application.booking.port.PaymentReconcileQueue;
import com.flashsale.domain.order.IdempotencyKey;
import com.flashsale.domain.order.Order;
import com.flashsale.domain.order.PaymentLine;
import com.flashsale.domain.order.PaymentLineStatus;
import com.flashsale.domain.order.PaymentMethodCode;
import com.flashsale.domain.shared.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentOrchestrator")
class PaymentOrchestratorImplTest {

    @Mock
    private PaymentGateway ypointGateway;

    @Mock
    private PaymentGateway creditCardGateway;

    @Mock
    private PaymentEventRepository eventRepository;

    @Mock
    private PaymentReconcileQueue reconcileQueue;

    private PaymentOrchestratorImpl orchestrator;

    @BeforeEach
    void setUp() {
        Mockito.lenient().when(ypointGateway.supports()).thenReturn(PaymentMethodCode.YPOINT);
        Mockito.lenient().when(creditCardGateway.supports()).thenReturn(PaymentMethodCode.CREDIT_CARD);
        orchestrator = new PaymentOrchestratorImpl(
                List.of(ypointGateway, creditCardGateway),
                eventRepository,
                reconcileQueue
        );
    }

    private Order createOrder() {
        return Order.create(
                IdempotencyKey.of("test-idem-key-1"),
                1L,
                1L,
                Money.of(50_000),
                LocalDateTime.now().plusMinutes(10)
        );
    }

    private PaymentLine createPaymentLine(
            final PaymentMethodCode method,
            final long amount,
            final Long id
    ) {
        int sequence = method.isPoint() ? 1 : 2;
        return PaymentLine.restore(
                id,
                method,
                Money.of(amount),
                PaymentLineStatus.REQUESTED,
                null,
                sequence
        );
    }

    private PaymentLineCommand pointCommand(
            final long amount,
            final String idempotencyKey
    ) {
        return new PaymentLineCommand(
                1,
                PaymentMethodCode.YPOINT,
                amount,
                null,
                idempotencyKey
        );
    }

    private PaymentLineCommand cardCommand(
            final long amount,
            final String idempotencyKey
    ) {
        return new PaymentLineCommand(
                2,
                PaymentMethodCode.CREDIT_CARD,
                amount,
                "4111111111111111",
                idempotencyKey
        );
    }

    @Nested
    @DisplayName("단독 결제")
    class SinglePayment {

        @Test
        @DisplayName("포인트 결제가 성공하면 AllPaid를 반환한다")
        void pointSuccess_returnsAllPaid() {
            Order order = createOrder();
            order.addPaymentLine(createPaymentLine(PaymentMethodCode.YPOINT, 30_000L, 1L));
            PaymentLineCommand cmd = pointCommand(30_000L, "idem-yp-1");

            when(ypointGateway.charge(cmd))
                    .thenReturn(new PaymentResult.Success("pg-tx-001"));

            OrchestratorResult result = orchestrator.execute(order, List.of(cmd));

            assertThat(result).isInstanceOf(OrchestratorResult.AllPaid.class);
        }

        @Test
        @DisplayName("결제 성공 시 CHARGE_REQUESTED 다음 CHARGE_APPROVED 순서로 이벤트가 기록된다")
        void chargeSuccess_recordsEventsInOrder() {
            Order order = createOrder();
            order.addPaymentLine(createPaymentLine(PaymentMethodCode.YPOINT, 30_000L, 1L));
            PaymentLineCommand cmd = pointCommand(30_000L, "idem-yp-order");

            when(ypointGateway.charge(cmd))
                    .thenReturn(new PaymentResult.Success("pg-tx-002"));

            orchestrator.execute(order, List.of(cmd));

            InOrder order_ = inOrder(eventRepository);
            order_.verify(eventRepository).save(
                    1L,
                    PaymentEventType.CHARGE_REQUESTED,
                    null,
                    "idem-yp-order"
            );
            order_.verify(eventRepository).save(
                    1L,
                    PaymentEventType.CHARGE_APPROVED,
                    "pg-tx-002",
                    null
            );
        }

        @Test
        @DisplayName("카드 결제가 실패하면 Failed를 반환한다")
        void cardFailure_returnsFailed() {
            Order order = createOrder();
            order.addPaymentLine(createPaymentLine(PaymentMethodCode.CREDIT_CARD, 50_000L, 2L));
            PaymentLineCommand cmd = cardCommand(50_000L, "idem-card-1");

            when(creditCardGateway.charge(cmd))
                    .thenReturn(new PaymentResult.Failure("INSUFFICIENT_FUNDS", "잔액 부족"));

            OrchestratorResult result = orchestrator.execute(order, List.of(cmd));

            assertThat(result).isInstanceOf(OrchestratorResult.Failed.class);
        }

        @Test
        @DisplayName("결제 실패 시 CHARGE_DECLINED 이벤트와 사유 코드가 기록된다")
        void chargeFailure_recordsDeclinedEvent() {
            Order order = createOrder();
            order.addPaymentLine(createPaymentLine(PaymentMethodCode.CREDIT_CARD, 50_000L, 2L));
            PaymentLineCommand cmd = cardCommand(50_000L, "idem-card-2");

            when(creditCardGateway.charge(cmd))
                    .thenReturn(new PaymentResult.Failure("INSUFFICIENT_FUNDS", "잔액 부족"));

            orchestrator.execute(order, List.of(cmd));

            verify(eventRepository).save(
                    2L,
                    PaymentEventType.CHARGE_DECLINED,
                    null,
                    "INSUFFICIENT_FUNDS"
            );
        }

        @Test
        @DisplayName("승인 라인 없이 단독 결제가 실패하면 보상 없이 종료된다")
        void singleFailure_noCompensation() {
            Order order = createOrder();
            order.addPaymentLine(createPaymentLine(PaymentMethodCode.CREDIT_CARD, 50_000L, 2L));
            PaymentLineCommand cmd = cardCommand(50_000L, "idem-card-3");

            when(creditCardGateway.charge(cmd))
                    .thenReturn(new PaymentResult.Failure("CARD_DECLINED", "거절"));

            orchestrator.execute(order, List.of(cmd));

            verify(reconcileQueue, never()).enqueue(any(), anyLong(), anyLong(), anyString());
        }

        @Test
        @DisplayName("결제가 불확실하면 Uncertain을 반환한다")
        void chargeUnknown_returnsUncertain() {
            Order order = createOrder();
            order.addPaymentLine(createPaymentLine(PaymentMethodCode.YPOINT, 30_000L, 1L));
            PaymentLineCommand cmd = pointCommand(30_000L, "idem-yp-uncertain");

            when(ypointGateway.charge(cmd))
                    .thenReturn(new PaymentResult.Unknown("idem-yp-uncertain"));

            OrchestratorResult result = orchestrator.execute(order, List.of(cmd));

            assertThat(result).isInstanceOf(OrchestratorResult.Uncertain.class);
        }

        @Test
        @DisplayName("결제 불확실 시 CHARGE_UNCERTAIN 이벤트가 기록되고 재검증 큐에 등록된다")
        void chargeUnknown_recordsEventAndEnqueuesInquiry() {
            Order order = createOrder();
            order.addPaymentLine(createPaymentLine(PaymentMethodCode.YPOINT, 30_000L, 1L));
            PaymentLineCommand cmd = pointCommand(30_000L, "idem-yp-inq");

            when(ypointGateway.charge(cmd))
                    .thenReturn(new PaymentResult.Unknown("idem-yp-inq"));

            orchestrator.execute(order, List.of(cmd));

            verify(eventRepository).save(
                    1L,
                    PaymentEventType.CHARGE_UNCERTAIN,
                    null,
                    null
            );
            verify(reconcileQueue).enqueue(
                    eq(PaymentReconcileQueue.ReconcileType.PAYMENT_INQUIRY),
                    any(),
                    eq(1L),
                    eq("idem-yp-inq")
            );
        }
    }

    @Nested
    @DisplayName("복합 결제 성공")
    class CompositePaymentSuccess {

        @Test
        @DisplayName("포인트와 카드 모두 성공하면 AllPaid를 반환한다")
        void pointAndCardSuccess_returnsAllPaid() {
            Order order = createOrder();
            order.addPaymentLine(createPaymentLine(PaymentMethodCode.YPOINT, 20_000L, 1L));
            order.addPaymentLine(createPaymentLine(PaymentMethodCode.CREDIT_CARD, 30_000L, 2L));
            PaymentLineCommand pointCmd = pointCommand(20_000L, "idem-yp-comp");
            PaymentLineCommand cardCmd = cardCommand(30_000L, "idem-card-comp");

            when(ypointGateway.charge(pointCmd))
                    .thenReturn(new PaymentResult.Success("pg-tx-yp-comp"));
            when(creditCardGateway.charge(cardCmd))
                    .thenReturn(new PaymentResult.Success("pg-tx-card-comp"));

            OrchestratorResult result = orchestrator.execute(order, List.of(pointCmd, cardCmd));

            assertThat(result).isInstanceOf(OrchestratorResult.AllPaid.class);
        }

        @Test
        @DisplayName("복합 결제는 포인트를 먼저 처리한 뒤 카드를 처리한다")
        void compositePayment_processesPointBeforeCard() {
            Order order = createOrder();
            order.addPaymentLine(createPaymentLine(PaymentMethodCode.YPOINT, 20_000L, 1L));
            order.addPaymentLine(createPaymentLine(PaymentMethodCode.CREDIT_CARD, 30_000L, 2L));
            PaymentLineCommand pointCmd = pointCommand(20_000L, "idem-yp-order");
            PaymentLineCommand cardCmd = cardCommand(30_000L, "idem-card-order");

            when(ypointGateway.charge(pointCmd))
                    .thenReturn(new PaymentResult.Success("pg-tx-yp"));
            when(creditCardGateway.charge(cardCmd))
                    .thenReturn(new PaymentResult.Success("pg-tx-card"));

            orchestrator.execute(order, List.of(pointCmd, cardCmd));

            InOrder order_ = inOrder(ypointGateway, creditCardGateway);
            order_.verify(ypointGateway).charge(pointCmd);
            order_.verify(creditCardGateway).charge(cardCmd);
        }
    }

    @Nested
    @DisplayName("복합 결제 부분 실패 보상")
    class CompositePaymentCompensation {

        @Test
        @DisplayName("포인트 성공 후 카드 실패 시 포인트 취소가 성공하면 Failed를 반환한다")
        void cardFailure_pointCancelSuccess_returnsFailed() {
            Order order = createOrder();
            order.addPaymentLine(createPaymentLine(PaymentMethodCode.YPOINT, 20_000L, 1L));
            order.addPaymentLine(createPaymentLine(PaymentMethodCode.CREDIT_CARD, 30_000L, 2L));
            PaymentLineCommand pointCmd = pointCommand(20_000L, "idem-yp-c");
            PaymentLineCommand cardCmd = cardCommand(30_000L, "idem-card-c");

            when(ypointGateway.charge(pointCmd))
                    .thenReturn(new PaymentResult.Success("pg-tx-yp"));
            when(creditCardGateway.charge(cardCmd))
                    .thenReturn(new PaymentResult.Failure("CARD_DECLINED", "카드 거절"));
            when(ypointGateway.cancel("pg-tx-yp"))
                    .thenReturn(new PaymentResult.Success("pg-tx-cancel"));

            OrchestratorResult result = orchestrator.execute(order, List.of(pointCmd, cardCmd));

            assertThat(result).isInstanceOf(OrchestratorResult.Failed.class);
        }

        @Test
        @DisplayName("보상 성공 시 CANCEL_REQUESTED 다음 CANCEL_APPROVED 순서로 이벤트가 기록된다")
        void compensationSuccess_recordsEventsInOrder() {
            Order order = createOrder();
            order.addPaymentLine(createPaymentLine(PaymentMethodCode.YPOINT, 20_000L, 1L));
            order.addPaymentLine(createPaymentLine(PaymentMethodCode.CREDIT_CARD, 30_000L, 2L));
            PaymentLineCommand pointCmd = pointCommand(20_000L, "idem-yp-ev");
            PaymentLineCommand cardCmd = cardCommand(30_000L, "idem-card-ev");

            when(ypointGateway.charge(pointCmd))
                    .thenReturn(new PaymentResult.Success("pg-tx-yp-ev"));
            when(creditCardGateway.charge(cardCmd))
                    .thenReturn(new PaymentResult.Failure("CARD_DECLINED", "거절"));
            when(ypointGateway.cancel("pg-tx-yp-ev"))
                    .thenReturn(new PaymentResult.Success("pg-tx-cancel-ev"));

            orchestrator.execute(order, List.of(pointCmd, cardCmd));

            InOrder order_ = inOrder(eventRepository);
            order_.verify(eventRepository).save(
                    1L,
                    PaymentEventType.CANCEL_REQUESTED,
                    "pg-tx-yp-ev",
                    null
            );
            order_.verify(eventRepository).save(
                    1L,
                    PaymentEventType.CANCEL_APPROVED,
                    null,
                    null
            );
        }

        @Test
        @DisplayName("포인트 성공 후 카드 실패 시 포인트 취소가 불확실하면 Compensating을 반환한다")
        void cardFailure_pointCancelUnknown_returnsCompensating() {
            Order order = createOrder();
            order.addPaymentLine(createPaymentLine(PaymentMethodCode.YPOINT, 20_000L, 1L));
            order.addPaymentLine(createPaymentLine(PaymentMethodCode.CREDIT_CARD, 30_000L, 2L));
            PaymentLineCommand pointCmd = pointCommand(20_000L, "idem-yp-u");
            PaymentLineCommand cardCmd = cardCommand(30_000L, "idem-card-u");

            when(ypointGateway.charge(pointCmd))
                    .thenReturn(new PaymentResult.Success("pg-tx-yp-u"));
            when(creditCardGateway.charge(cardCmd))
                    .thenReturn(new PaymentResult.Failure("CARD_DECLINED", "거절"));
            when(ypointGateway.cancel("pg-tx-yp-u"))
                    .thenReturn(new PaymentResult.Unknown("cancel-uncertain"));

            OrchestratorResult result = orchestrator.execute(order, List.of(pointCmd, cardCmd));

            assertThat(result).isInstanceOf(OrchestratorResult.Compensating.class);
        }

        @Test
        @DisplayName("취소 불확실 시 CANCEL_UNCERTAIN 이벤트와 CANCEL_RETRY 큐 등록이 수행된다")
        void cancelUnknown_recordsEventAndEnqueuesRetry() {
            Order order = createOrder();
            order.addPaymentLine(createPaymentLine(PaymentMethodCode.YPOINT, 20_000L, 1L));
            order.addPaymentLine(createPaymentLine(PaymentMethodCode.CREDIT_CARD, 30_000L, 2L));
            PaymentLineCommand pointCmd = pointCommand(20_000L, "idem-yp-u2");
            PaymentLineCommand cardCmd = cardCommand(30_000L, "idem-card-u2");

            when(ypointGateway.charge(pointCmd))
                    .thenReturn(new PaymentResult.Success("pg-tx-yp-u2"));
            when(creditCardGateway.charge(cardCmd))
                    .thenReturn(new PaymentResult.Failure("CARD_DECLINED", "거절"));
            when(ypointGateway.cancel("pg-tx-yp-u2"))
                    .thenReturn(new PaymentResult.Unknown("cancel-uncertain-2"));

            orchestrator.execute(order, List.of(pointCmd, cardCmd));

            verify(eventRepository).save(
                    1L,
                    PaymentEventType.CANCEL_UNCERTAIN,
                    null,
                    null
            );
            verify(reconcileQueue).enqueue(
                    eq(PaymentReconcileQueue.ReconcileType.CANCEL_RETRY),
                    any(),
                    eq(1L),
                    eq("cancel-uncertain-2")
            );
        }

        @Test
        @DisplayName("포인트 성공 후 카드 실패 시 포인트 취소가 명시적으로 실패하면 Compensating을 반환하고 재시도 큐에 등록한다")
        void cardFailure_pointCancelExplicitFailure_returnsCompensatingAndEnqueues() {
            Order order = createOrder();
            order.addPaymentLine(createPaymentLine(PaymentMethodCode.YPOINT, 20_000L, 1L));
            order.addPaymentLine(createPaymentLine(PaymentMethodCode.CREDIT_CARD, 30_000L, 2L));
            PaymentLineCommand pointCmd = pointCommand(20_000L, "idem-yp-f");
            PaymentLineCommand cardCmd = cardCommand(30_000L, "idem-card-f");

            when(ypointGateway.charge(pointCmd))
                    .thenReturn(new PaymentResult.Success("pg-tx-yp-f"));
            when(creditCardGateway.charge(cardCmd))
                    .thenReturn(new PaymentResult.Failure("CARD_DECLINED", "거절"));
            when(ypointGateway.cancel("pg-tx-yp-f"))
                    .thenReturn(new PaymentResult.Failure("ALREADY_SETTLED", "이미 정산됨"));

            OrchestratorResult result = orchestrator.execute(order, List.of(pointCmd, cardCmd));

            assertThat(result).isInstanceOf(OrchestratorResult.Compensating.class);
            verify(eventRepository).save(
                    1L,
                    PaymentEventType.CANCEL_DECLINED,
                    null,
                    "ALREADY_SETTLED"
            );
            verify(reconcileQueue).enqueue(
                    eq(PaymentReconcileQueue.ReconcileType.CANCEL_RETRY),
                    any(),
                    eq(1L),
                    anyString()
            );
        }

        @Test
        @DisplayName("취소 중 게이트웨이 예외 발생 시 Compensating을 반환하고 재시도 큐에 등록한다")
        void cancelGatewayException_returnsCompensatingAndEnqueues() {
            Order order = createOrder();
            order.addPaymentLine(createPaymentLine(PaymentMethodCode.YPOINT, 20_000L, 1L));
            order.addPaymentLine(createPaymentLine(PaymentMethodCode.CREDIT_CARD, 30_000L, 2L));
            PaymentLineCommand pointCmd = pointCommand(20_000L, "idem-yp-ex");
            PaymentLineCommand cardCmd = cardCommand(30_000L, "idem-card-ex");

            when(ypointGateway.charge(pointCmd))
                    .thenReturn(new PaymentResult.Success("pg-tx-yp-ex"));
            when(creditCardGateway.charge(cardCmd))
                    .thenReturn(new PaymentResult.Failure("CARD_DECLINED", "거절"));
            when(ypointGateway.cancel("pg-tx-yp-ex"))
                    .thenThrow(new RuntimeException("PG 연결 오류"));

            OrchestratorResult result = orchestrator.execute(order, List.of(pointCmd, cardCmd));

            assertThat(result).isInstanceOf(OrchestratorResult.Compensating.class);
            verify(reconcileQueue).enqueue(
                    eq(PaymentReconcileQueue.ReconcileType.CANCEL_RETRY),
                    any(),
                    eq(1L),
                    eq("cancel-1")
            );
            verify(eventRepository, never()).save(
                    any(), eq(PaymentEventType.CANCEL_APPROVED), any(), any()
            );
            verify(eventRepository, never()).save(
                    any(), eq(PaymentEventType.CANCEL_DECLINED), any(), any()
            );
        }
    }
}