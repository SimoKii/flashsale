package com.flashsale.application.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.application.booking.dto.BookingAcceptedResult;
import com.flashsale.application.booking.dto.BookingCommand;
import com.flashsale.application.booking.dto.BookingStatusResult;
import com.flashsale.application.booking.dto.PaymentLineCommand;
import com.flashsale.application.booking.exception.DuplicateBookingException;
import com.flashsale.application.booking.exception.InvalidPaymentCompositionException;
import com.flashsale.application.booking.port.BookingQueuePort;
import com.flashsale.application.booking.port.BookingResultStore;
import com.flashsale.application.booking.port.IdempotencyStore;
import com.flashsale.application.booking.port.UserEntryGuardPort;
import com.flashsale.common.exception.DomainException;
import com.flashsale.domain.order.PaymentComposition;
import com.flashsale.domain.order.PaymentMethodCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookingUsecase")
class BookingUsecaseImplTest {

    @Mock
    private IdempotencyStore idempotencyStore;

    @Mock
    private UserEntryGuardPort userEntryGuardPort;

    @Mock
    private BookingQueuePort bookingQueuePort;

    @Mock
    private BookingResultStore bookingResultStore;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private BookingUsecaseImpl bookingUsecase;

    private static final Long PRODUCT_ID = 1L;
    private static final Long USER_ID = 42L;
    private static final String IDEMPOTENCY_KEY = "idem-key-001";
    private static final String TICKET_ID = "ticket-stream-id-001";

    private BookingCommand validCommand() {
        return new BookingCommand(
                PRODUCT_ID,
                USER_ID,
                IDEMPOTENCY_KEY,
                10_000L,
                List.of(new PaymentLineCommand(
                        2,
                        PaymentMethodCode.CREDIT_CARD,
                        10_000L,
                        "1234-5678",
                        "pay-idem-001",
                        null
                ))
        );
    }

    @Nested
    @DisplayName("예약 요청")
    class Book {

        @Test
        @DisplayName("유효한 요청은 BookingAcceptedResult를 반환한다")
        void validRequest_returnsAcceptedResult() throws Exception {
            BookingCommand cmd = validCommand();
            when(idempotencyStore.setIfAbsent(eq(IDEMPOTENCY_KEY), eq("PENDING"), any(Duration.class)))
                    .thenReturn(true);
            when(userEntryGuardPort.acquire(eq(PRODUCT_ID), eq(USER_ID), any(Duration.class)))
                    .thenReturn(true);
            when(objectMapper.writeValueAsString(any())).thenReturn("[]");
            when(bookingQueuePort.enqueue(eq(PRODUCT_ID), anyMap())).thenReturn(TICKET_ID);

            try (MockedStatic<PaymentComposition> mocked = mockStatic(PaymentComposition.class)) {
                mocked.when(() -> PaymentComposition.validate(any(), any())).thenAnswer(inv -> null);

                BookingAcceptedResult result = bookingUsecase.book(cmd);

                assertThat(result.ticketId()).isEqualTo(TICKET_ID);
                assertThat(result.queuePosition()).isEqualTo(0L);
            }
        }

        @Test
        @DisplayName("정상 처리 후 멱등성 키가 ticketId로 갱신된다")
        void onSuccess_updatesIdempotencyKey() throws Exception {
            BookingCommand cmd = validCommand();
            when(idempotencyStore.setIfAbsent(eq(IDEMPOTENCY_KEY), eq("PENDING"), any(Duration.class)))
                    .thenReturn(true);
            when(userEntryGuardPort.acquire(eq(PRODUCT_ID), eq(USER_ID), any(Duration.class)))
                    .thenReturn(true);
            when(objectMapper.writeValueAsString(any())).thenReturn("[]");
            when(bookingQueuePort.enqueue(eq(PRODUCT_ID), anyMap())).thenReturn(TICKET_ID);

            try (MockedStatic<PaymentComposition> mocked = mockStatic(PaymentComposition.class)) {
                mocked.when(() -> PaymentComposition.validate(any(), any())).thenAnswer(inv -> null);

                bookingUsecase.book(cmd);

                verify(idempotencyStore).update(
                        eq(IDEMPOTENCY_KEY),
                        eq(TICKET_ID),
                        any(Duration.class)
                );
            }
        }

        @Test
        @DisplayName("이미 완료된 멱등성 키 재요청 시 캐시된 ticketId를 반환한다")
        void duplicateKey_completed_returnsCachedTicketId() {
            BookingCommand cmd = validCommand();
            when(idempotencyStore.setIfAbsent(eq(IDEMPOTENCY_KEY), eq("PENDING"), any(Duration.class)))
                    .thenReturn(false);
            when(idempotencyStore.get(eq(IDEMPOTENCY_KEY))).thenReturn(Optional.of(TICKET_ID));

            BookingAcceptedResult result = bookingUsecase.book(cmd);

            assertThat(result.ticketId()).isEqualTo(TICKET_ID);
            assertThat(result.queuePosition()).isEqualTo(0L);
        }

        @Test
        @DisplayName("이미 완료된 멱등성 키 재요청 시 후속 단계는 실행되지 않는다")
        void duplicateKey_completed_skipsSubsequentSteps() {
            BookingCommand cmd = validCommand();
            when(idempotencyStore.setIfAbsent(eq(IDEMPOTENCY_KEY), eq("PENDING"), any(Duration.class)))
                    .thenReturn(false);
            when(idempotencyStore.get(eq(IDEMPOTENCY_KEY))).thenReturn(Optional.of(TICKET_ID));

            bookingUsecase.book(cmd);

            verify(userEntryGuardPort, never()).acquire(anyLong(), anyLong(), any(Duration.class));
            verify(bookingQueuePort, never()).enqueue(anyLong(), anyMap());
        }

        @Test
        @DisplayName("PENDING 상태의 동일 키 재요청은 DuplicateBookingException을 던진다")
        void duplicateKey_pending_throwsDuplicateBooking() {
            BookingCommand cmd = validCommand();
            when(idempotencyStore.setIfAbsent(eq(IDEMPOTENCY_KEY), eq("PENDING"), any(Duration.class)))
                    .thenReturn(false);
            when(idempotencyStore.get(eq(IDEMPOTENCY_KEY))).thenReturn(Optional.of("PENDING"));

            assertThatThrownBy(() -> bookingUsecase.book(cmd))
                    .isInstanceOf(DuplicateBookingException.class)
                    .hasMessageContaining(IDEMPOTENCY_KEY);
        }

        @Test
        @DisplayName("결제 조합이 유효하지 않으면 InvalidPaymentCompositionException을 던진다")
        void invalidComposition_throwsInvalidPaymentComposition() {
            BookingCommand cmd = validCommand();
            when(idempotencyStore.setIfAbsent(eq(IDEMPOTENCY_KEY), eq("PENDING"), any(Duration.class)))
                    .thenReturn(true);

            try (MockedStatic<PaymentComposition> mocked = mockStatic(PaymentComposition.class)) {
                mocked.when(() -> PaymentComposition.validate(any(), any()))
                        .thenThrow(new DomainException("Payment line sum mismatch"));

                assertThatThrownBy(() -> bookingUsecase.book(cmd))
                        .isInstanceOf(InvalidPaymentCompositionException.class)
                        .hasMessageContaining("Payment line sum mismatch");
            }
        }

        @Test
        @DisplayName("결제 조합 검증 실패 시 사용자 진입 가드 획득을 시도하지 않는다")
        void invalidComposition_doesNotAcquireGuard() {
            BookingCommand cmd = validCommand();
            when(idempotencyStore.setIfAbsent(eq(IDEMPOTENCY_KEY), eq("PENDING"), any(Duration.class)))
                    .thenReturn(true);

            try (MockedStatic<PaymentComposition> mocked = mockStatic(PaymentComposition.class)) {
                mocked.when(() -> PaymentComposition.validate(any(), any()))
                        .thenThrow(new DomainException("Payment line sum mismatch"));

                assertThatThrownBy(() -> bookingUsecase.book(cmd))
                        .isInstanceOf(InvalidPaymentCompositionException.class);
            }

            verify(userEntryGuardPort, never()).acquire(anyLong(), anyLong(), any(Duration.class));
        }

        @Test
        @DisplayName("결제 조합 검증 실패 시 멱등성 키를 INVALID로 초기화한다")
        void book_validationFails_clearsIdempotencyKey() {
            BookingCommand cmd = validCommand();
            when(idempotencyStore.setIfAbsent(any(), eq("PENDING"), any())).thenReturn(true);

            try (MockedStatic<PaymentComposition> mocked = mockStatic(PaymentComposition.class)) {
                mocked.when(() -> PaymentComposition.validate(any(), any()))
                        .thenThrow(new DomainException("bad composition"));

                assertThatThrownBy(() -> bookingUsecase.book(cmd))
                        .isInstanceOf(InvalidPaymentCompositionException.class);

                verify(idempotencyStore).update(eq(cmd.idempotencyKey()), eq("INVALID"), eq(Duration.ofSeconds(5)));
            }
        }

        @Test
        @DisplayName("사용자 진입 가드 획득 실패 시 DuplicateBookingException을 던진다")
        void guardAcquireFails_throwsDuplicateBooking() {
            BookingCommand cmd = validCommand();
            when(idempotencyStore.setIfAbsent(eq(IDEMPOTENCY_KEY), eq("PENDING"), any(Duration.class)))
                    .thenReturn(true);
            when(userEntryGuardPort.acquire(eq(PRODUCT_ID), eq(USER_ID), any(Duration.class)))
                    .thenReturn(false);

            try (MockedStatic<PaymentComposition> mocked = mockStatic(PaymentComposition.class)) {
                mocked.when(() -> PaymentComposition.validate(any(), any())).thenAnswer(inv -> null);

                assertThatThrownBy(() -> bookingUsecase.book(cmd))
                        .isInstanceOf(DuplicateBookingException.class)
                        .hasMessageContaining(USER_ID.toString());
            }
        }

        @Test
        @DisplayName("가드 획득 실패 시 큐에 등록하지 않는다")
        void guardAcquireFails_doesNotEnqueue() {
            BookingCommand cmd = validCommand();
            when(idempotencyStore.setIfAbsent(eq(IDEMPOTENCY_KEY), eq("PENDING"), any(Duration.class)))
                    .thenReturn(true);
            when(userEntryGuardPort.acquire(eq(PRODUCT_ID), eq(USER_ID), any(Duration.class)))
                    .thenReturn(false);

            try (MockedStatic<PaymentComposition> mocked = mockStatic(PaymentComposition.class)) {
                mocked.when(() -> PaymentComposition.validate(any(), any())).thenAnswer(inv -> null);

                assertThatThrownBy(() -> bookingUsecase.book(cmd))
                        .isInstanceOf(DuplicateBookingException.class);
            }

            verify(bookingQueuePort, never()).enqueue(anyLong(), anyMap());
        }

        @Test
        @DisplayName("큐 등록 실패 시 가드를 해제하고 예외를 그대로 전파한다")
        void enqueueFails_releasesGuardAndRethrows() throws Exception {
            BookingCommand cmd = validCommand();
            RuntimeException enqueueException = new RuntimeException("Redis stream error");
            when(idempotencyStore.setIfAbsent(eq(IDEMPOTENCY_KEY), eq("PENDING"), any(Duration.class)))
                    .thenReturn(true);
            when(userEntryGuardPort.acquire(eq(PRODUCT_ID), eq(USER_ID), any(Duration.class)))
                    .thenReturn(true);
            when(objectMapper.writeValueAsString(any())).thenReturn("[]");
            when(bookingQueuePort.enqueue(eq(PRODUCT_ID), anyMap())).thenThrow(enqueueException);

            try (MockedStatic<PaymentComposition> mocked = mockStatic(PaymentComposition.class)) {
                mocked.when(() -> PaymentComposition.validate(any(), any())).thenAnswer(inv -> null);

                assertThatThrownBy(() -> bookingUsecase.book(cmd))
                        .isSameAs(enqueueException);
            }

            verify(userEntryGuardPort).release(eq(PRODUCT_ID), eq(USER_ID));
        }

        @Test
        @DisplayName("큐 등록 실패 시 멱등성 키를 INVALID로 초기화한다")
        void enqueueFails_clearsIdempotencyKey() throws Exception {
            BookingCommand cmd = validCommand();
            when(idempotencyStore.setIfAbsent(eq(IDEMPOTENCY_KEY), eq("PENDING"), any(Duration.class)))
                    .thenReturn(true);
            when(userEntryGuardPort.acquire(eq(PRODUCT_ID), eq(USER_ID), any(Duration.class)))
                    .thenReturn(true);
            when(objectMapper.writeValueAsString(any())).thenReturn("[]");
            when(bookingQueuePort.enqueue(eq(PRODUCT_ID), anyMap()))
                    .thenThrow(new RuntimeException("Redis error"));

            try (MockedStatic<PaymentComposition> mocked = mockStatic(PaymentComposition.class)) {
                mocked.when(() -> PaymentComposition.validate(any(), any())).thenAnswer(inv -> null);

                assertThatThrownBy(() -> bookingUsecase.book(cmd))
                        .isInstanceOf(RuntimeException.class);
            }

            verify(idempotencyStore).update(eq(IDEMPOTENCY_KEY), eq("INVALID"), eq(Duration.ofSeconds(5)));
        }
    }

    @Nested
    @DisplayName("예약 결과 조회")
    class GetBookingStatus {

        @Test
        @DisplayName("결과가 없으면 Pending을 반환한다")
        void notFound_returnsPending() {
            when(bookingResultStore.find(eq(PRODUCT_ID), eq(TICKET_ID)))
                    .thenReturn(Optional.empty());

            BookingStatusResult result = bookingUsecase.getBookingStatus(PRODUCT_ID, TICKET_ID);

            assertThat(result).isInstanceOf(BookingStatusResult.Pending.class);
        }

        @Test
        @DisplayName("JSON 결과는 Paid로 변환된다")
        void jsonResult_returnsPaid() throws Exception {
            String body = "{\"orderId\":123}";
            when(bookingResultStore.find(eq(PRODUCT_ID), eq(TICKET_ID)))
                    .thenReturn(Optional.of(body));

            ObjectMapper realMapper = new ObjectMapper();
            when(objectMapper.readTree(body)).thenReturn(realMapper.readTree(body));

            BookingStatusResult result = bookingUsecase.getBookingStatus(PRODUCT_ID, TICKET_ID);

            assertThat(result).isInstanceOf(BookingStatusResult.Paid.class);
        }

        @Test
        @DisplayName("Paid 결과는 주문 ID와 응답 본문을 포함한다")
        void paidResult_containsOrderIdAndBody() throws Exception {
            String body = "{\"orderId\":123}";
            when(bookingResultStore.find(eq(PRODUCT_ID), eq(TICKET_ID)))
                    .thenReturn(Optional.of(body));

            ObjectMapper realMapper = new ObjectMapper();
            when(objectMapper.readTree(body)).thenReturn(realMapper.readTree(body));

            BookingStatusResult result = bookingUsecase.getBookingStatus(PRODUCT_ID, TICKET_ID);

            BookingStatusResult.Paid paid = (BookingStatusResult.Paid) result;
            assertThat(paid.orderId()).isEqualTo(123L);
            assertThat(paid.responseBody()).isEqualTo(body);
        }

        @Test
        @DisplayName("FAILED 형식 결과는 Failed로 변환된다")
        void failedFormatResult_returnsFailed() {
            String body = "FAILED:ERR_001:some error";
            when(bookingResultStore.find(eq(PRODUCT_ID), eq(TICKET_ID)))
                    .thenReturn(Optional.of(body));

            BookingStatusResult result = bookingUsecase.getBookingStatus(PRODUCT_ID, TICKET_ID);

            assertThat(result).isInstanceOf(BookingStatusResult.Failed.class);
        }

        @Test
        @DisplayName("Failed 결과는 사유 코드와 메시지를 포함한다")
        void failedResult_containsCodeAndMessage() {
            String body = "FAILED:ERR_001:some error";
            when(bookingResultStore.find(eq(PRODUCT_ID), eq(TICKET_ID)))
                    .thenReturn(Optional.of(body));

            BookingStatusResult result = bookingUsecase.getBookingStatus(PRODUCT_ID, TICKET_ID);

            BookingStatusResult.Failed failed = (BookingStatusResult.Failed) result;
            assertThat(failed.code()).isEqualTo("ERR_001");
            assertThat(failed.message()).isEqualTo("some error");
        }

        @Test
        @DisplayName("UNCERTAIN 형식 결과는 Uncertain으로 변환된다")
        void uncertainFormatResult_returnsUncertain() {
            String body = "UNCERTAIN:timeout";
            when(bookingResultStore.find(eq(PRODUCT_ID), eq(TICKET_ID)))
                    .thenReturn(Optional.of(body));

            BookingStatusResult result = bookingUsecase.getBookingStatus(PRODUCT_ID, TICKET_ID);

            assertThat(result).isInstanceOf(BookingStatusResult.Uncertain.class);
            BookingStatusResult.Uncertain uncertain = (BookingStatusResult.Uncertain) result;
            assertThat(uncertain.message()).isEqualTo("timeout");
        }

        @Test
        @DisplayName("존재하지 않는 티켓 조회는 NotFound가 아닌 Pending을 반환한다")
        void nonExistingTicket_returnsPendingNotNotFound() {
            String nonExisting = "nonexistent-ticket";
            when(bookingResultStore.find(eq(PRODUCT_ID), eq(nonExisting)))
                    .thenReturn(Optional.empty());

            BookingStatusResult result = bookingUsecase.getBookingStatus(PRODUCT_ID, nonExisting);

            assertThat(result).isInstanceOf(BookingStatusResult.Pending.class);
            assertThat(result).isNotInstanceOf(BookingStatusResult.NotFound.class);
        }
    }
}