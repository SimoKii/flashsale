package com.flashsale.infrastructure.jpa.reconcile;

import com.flashsale.application.booking.port.PaymentReconcileQueue;
import com.flashsale.application.booking.port.PaymentReconcileQueue.ReconcileItem;
import com.flashsale.application.booking.port.PaymentReconcileQueue.ReconcileType;
import com.flashsale.infrastructure.jpa.reconcile.impl.PaymentReconcileQueueImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(PaymentReconcileQueueImpl.class)
@DisplayName("PaymentReconcileQueue")
class PaymentReconcileQueueImplTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("flashsale")
            .withUsername("flashsale")
            .withPassword("flashsalepw");

    @DynamicPropertySource
    static void properties(
            final DynamicPropertyRegistry registry
    ) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    PaymentReconcileQueue queue;

    static final long ORDER_ID = 1L;
    static final long PAYMENT_LINE_ID = 1L;
    static final String IDEMPOTENCY_KEY = "test-idem-key-001";

    @Nested
    @DisplayName("큐 등록과 조회")
    class EnqueueAndFind {

        @Test
        @DisplayName("등록한 항목은 즉시 조회된다")
        void enqueuedItem_isImmediatelyFound() {
            queue.enqueue(ReconcileType.PAYMENT_INQUIRY, ORDER_ID, PAYMENT_LINE_ID, IDEMPOTENCY_KEY);

            List<ReconcileItem> items = queue.findReadyToRetry(10);

            assertThat(items).hasSize(1);
            assertThat(items.get(0).orderId()).isEqualTo(ORDER_ID);
            assertThat(items.get(0).paymentLineId()).isEqualTo(PAYMENT_LINE_ID);
            assertThat(items.get(0).type()).isEqualTo(ReconcileType.PAYMENT_INQUIRY);
            assertThat(items.get(0).idempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
            assertThat(items.get(0).retryCount()).isZero();
        }

        @Test
        @DisplayName("limit보다 많이 등록해도 limit 수만큼만 조회된다")
        void exceedingLimit_returnsLimited() {
            for (int i = 0; i < 5; i++) {
                queue.enqueue(ReconcileType.CANCEL_RETRY, ORDER_ID, null, buildKey(i));
            }

            List<ReconcileItem> items = queue.findReadyToRetry(3);

            assertThat(items).hasSize(3);
        }

        @Test
        @DisplayName("결제 라인 ID 없이도 등록할 수 있다")
        void nullPaymentLineId_canBeEnqueued() {
            queue.enqueue(ReconcileType.CANCEL_RETRY, ORDER_ID, null, IDEMPOTENCY_KEY);

            List<ReconcileItem> items = queue.findReadyToRetry(10);

            assertThat(items).hasSize(1);
            assertThat(items.get(0).paymentLineId()).isNull();
        }
    }

    @Nested
    @DisplayName("완료 처리")
    class MarkDone {

        @Test
        @DisplayName("완료 처리하면 재시도 대상에서 제외된다")
        void afterMarkDone_excludedFromRetry() {
            queue.enqueue(ReconcileType.PAYMENT_INQUIRY, ORDER_ID, PAYMENT_LINE_ID, IDEMPOTENCY_KEY);
            Long id = queue.findReadyToRetry(1).get(0).id();

            queue.markDone(id);

            assertThat(queue.findReadyToRetry(10)).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 ID로 완료 처리하면 예외가 발생한다")
        void unknownId_throwsException() {
            assertThatThrownBy(() -> queue.markDone(999_999L))
                    .hasMessageContaining("not found");
        }
    }

    @Nested
    @DisplayName("실패 처리")
    class MarkFailed {

        @Test
        @DisplayName("실패 처리하면 재시도 대상에서 제외된다")
        void afterMarkFailed_excludedFromRetry() {
            queue.enqueue(ReconcileType.PAYMENT_INQUIRY, ORDER_ID, PAYMENT_LINE_ID, IDEMPOTENCY_KEY);
            Long id = queue.findReadyToRetry(1).get(0).id();

            queue.markFailed(id, "PG_TIMEOUT");

            assertThat(queue.findReadyToRetry(10)).isEmpty();
        }
    }

    @Nested
    @DisplayName("재시도 예약")
    class ScheduleRetry {

        @Test
        @DisplayName("미래 시각으로 재시도 예약하면 현재 조회에서 제외된다")
        void scheduleToFuture_excludedFromCurrentQuery() {
            queue.enqueue(ReconcileType.PAYMENT_INQUIRY, ORDER_ID, PAYMENT_LINE_ID, IDEMPOTENCY_KEY);
            Long id = queue.findReadyToRetry(1).get(0).id();

            queue.scheduleRetry(id, LocalDateTime.now().plusHours(1));

            assertThat(queue.findReadyToRetry(10)).isEmpty();
        }

        @Test
        @DisplayName("재시도 예약하면 재시도 횟수가 증가한다")
        void schedule_incrementsRetryCount() {
            queue.enqueue(ReconcileType.PAYMENT_INQUIRY, ORDER_ID, PAYMENT_LINE_ID, IDEMPOTENCY_KEY);
            Long id = queue.findReadyToRetry(1).get(0).id();

            queue.scheduleRetry(id, LocalDateTime.now().minusSeconds(1));

            List<ReconcileItem> items = queue.findReadyToRetry(10);
            assertThat(items).hasSize(1);
            assertThat(items.get(0).retryCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("여러 번 재시도 예약하면 횟수가 누적된다")
        void multipleSchedules_accumulateRetryCount() {
            queue.enqueue(ReconcileType.PAYMENT_INQUIRY, ORDER_ID, PAYMENT_LINE_ID, IDEMPOTENCY_KEY);
            Long id = queue.findReadyToRetry(1).get(0).id();

            queue.scheduleRetry(id, LocalDateTime.now().minusSeconds(1));
            queue.scheduleRetry(id, LocalDateTime.now().minusSeconds(1));
            queue.scheduleRetry(id, LocalDateTime.now().minusSeconds(1));

            List<ReconcileItem> items = queue.findReadyToRetry(10);
            assertThat(items.get(0).retryCount()).isEqualTo(3);
        }
    }

    private String buildKey(
            final int index
    ) {
        return String.format("test-key-%010d", index);
    }
}