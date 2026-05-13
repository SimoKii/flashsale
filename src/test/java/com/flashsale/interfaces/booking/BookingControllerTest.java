package com.flashsale.interfaces.booking;

import com.flashsale.application.booking.BookingUsecase;
import com.flashsale.application.booking.dto.BookingAcceptedResult;
import com.flashsale.application.booking.dto.BookingStatusResult;
import com.flashsale.application.booking.exception.DuplicateBookingException;
import com.flashsale.application.booking.exception.InvalidPaymentCompositionException;
import com.flashsale.application.booking.exception.ServiceUnavailableException;
import com.flashsale.interfaces.common.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {BookingController.class, GlobalExceptionHandler.class})
@DisplayName("BookingController")
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BookingUsecase bookingUsecase;

    private static final String USER_ID = "42";
    private static final String IDEMPOTENCY_KEY = "idem-key-1";
    private static final String TICKET_ID = "ticket-1";
    private static final long PRODUCT_ID = 1L;

    private String validBookingBody() {
        return """
                {
                  "productId": 1,
                  "totalAmount": 50000,
                  "paymentLines": [
                    {
                      "sequence": 1,
                      "method": "YPOINT",
                      "amount": 50000,
                      "idempotencyKey": "idem-line-1"
                    }
                  ]
                }
                """;
    }

    private MockHttpServletRequestBuilder bookingRequest() {
        return post("/api/v1/booking")
                .header("X-User-Id", USER_ID)
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBookingBody());
    }

    private MockHttpServletRequestBuilder statusRequest() {
        return get("/api/v1/booking/status")
                .header("X-User-Id", USER_ID)
                .param("productId", String.valueOf(PRODUCT_ID))
                .param("ticketId", TICKET_ID);
    }

    @Nested
    @DisplayName("예약 요청 (POST)")
    class CreateBooking {

        @Test
        @DisplayName("유효한 요청은 202와 ticketId를 반환한다")
        void validRequest_returns202WithTicketId() throws Exception {
            when(bookingUsecase.book(any()))
                    .thenReturn(new BookingAcceptedResult(TICKET_ID, 0L));

            mockMvc.perform(bookingRequest())
                    .andExpect(status().isAccepted())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.data.ticketId").value(TICKET_ID));
        }

        @Test
        @DisplayName("응답에 큐 위치가 포함된다")
        void response_containsQueuePosition() throws Exception {
            when(bookingUsecase.book(any()))
                    .thenReturn(new BookingAcceptedResult(TICKET_ID, 5L));

            mockMvc.perform(bookingRequest())
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.data.queuePosition").value(5));
        }

        @Test
        @DisplayName("응답은 SUCCESS 코드를 포함한다")
        void response_containsSuccessCode() throws Exception {
            when(bookingUsecase.book(any()))
                    .thenReturn(new BookingAcceptedResult(TICKET_ID, 0L));

            mockMvc.perform(bookingRequest())
                    .andExpect(jsonPath("$.code").value("SUCCESS"));
        }
    }

    @Nested
    @DisplayName("예약 요청 검증")
    class CreateBookingValidation {

        @Test
        @DisplayName("X-User-Id 헤더가 없으면 400을 반환한다")
        void missingUserIdHeader_returns400() throws Exception {
            mockMvc.perform(post("/api/v1/booking")
                            .header("Idempotency-Key", IDEMPOTENCY_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validBookingBody()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Idempotency-Key 헤더가 없으면 400을 반환한다")
        void missingIdempotencyKeyHeader_returns400() throws Exception {
            mockMvc.perform(post("/api/v1/booking")
                            .header("X-User-Id", USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validBookingBody()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("요청 본문이 비어있으면 400을 반환한다")
        void emptyBody_returns400() throws Exception {
            mockMvc.perform(post("/api/v1/booking")
                            .header("X-User-Id", USER_ID)
                            .header("Idempotency-Key", IDEMPOTENCY_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(""))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("요청 본문이 JSON 형식이 아니면 400을 반환한다")
        void malformedJson_returns400() throws Exception {
            mockMvc.perform(post("/api/v1/booking")
                            .header("X-User-Id", USER_ID)
                            .header("Idempotency-Key", IDEMPOTENCY_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"productId\": 1"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Content-Type이 JSON이 아니면 415를 반환한다")
        void wrongContentType_returns415() throws Exception {
            mockMvc.perform(post("/api/v1/booking")
                            .header("X-User-Id", USER_ID)
                            .header("Idempotency-Key", IDEMPOTENCY_KEY)
                            .contentType(MediaType.TEXT_PLAIN)
                            .content(validBookingBody()))
                    .andExpect(status().isUnsupportedMediaType());
        }
    }

    @Nested
    @DisplayName("예약 요청 비즈니스 예외 처리")
    class CreateBookingExceptions {

        @Test
        @DisplayName("중복 예약 시 409와 DUPLICATE_BOOKING 코드를 반환한다")
        void duplicateBooking_returns409() throws Exception {
            when(bookingUsecase.book(any()))
                    .thenThrow(new DuplicateBookingException("이미 예약된 상품입니다"));

            mockMvc.perform(bookingRequest())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DUPLICATE_BOOKING"));
        }

        @Test
        @DisplayName("결제 구성 오류 시 400과 INVALID_PAYMENT_COMPOSITION 코드를 반환한다")
        void invalidPaymentComposition_returns400() throws Exception {
            when(bookingUsecase.book(any()))
                    .thenThrow(new InvalidPaymentCompositionException("결제 수단 구성이 올바르지 않습니다"));

            mockMvc.perform(bookingRequest())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_COMPOSITION"));
        }

        @Test
        @DisplayName("서비스 불가 시 503과 SERVICE_UNAVAILABLE 코드를 반환한다")
        void serviceUnavailable_returns503() throws Exception {
            when(bookingUsecase.book(any()))
                    .thenThrow(new ServiceUnavailableException("서비스를 사용할 수 없습니다"));

            mockMvc.perform(bookingRequest())
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
        }

        @Test
        @DisplayName("서비스 불가 응답에는 Retry-After 헤더가 포함된다")
        void serviceUnavailable_includesRetryAfterHeader() throws Exception {
            when(bookingUsecase.book(any()))
                    .thenThrow(new ServiceUnavailableException("서비스를 사용할 수 없습니다"));

            mockMvc.perform(bookingRequest())
                    .andExpect(header().string("Retry-After", "5"));
        }
    }

    @Nested
    @DisplayName("예약 결과 조회 (GET)")
    class GetBookingStatus {

        @Test
        @DisplayName("PENDING 상태는 PENDING 코드로 반환된다")
        void pendingStatus_returnsPendingCode() throws Exception {
            when(bookingUsecase.getBookingStatus(anyLong(), anyString()))
                    .thenReturn(new BookingStatusResult.Pending());

            mockMvc.perform(statusRequest())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PENDING"));
        }

        @Test
        @DisplayName("PAID 상태는 PAID 코드와 orderId를 반환한다")
        void paidStatus_returnsPaidCodeWithOrderId() throws Exception {
            when(bookingUsecase.getBookingStatus(anyLong(), anyString()))
                    .thenReturn(new BookingStatusResult.Paid(123L, "{}"));

            mockMvc.perform(statusRequest())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PAID"))
                    .andExpect(jsonPath("$.data.orderId").value(123))
                    .andExpect(jsonPath("$.data.responseBody").value("{}"));
        }

        @Test
        @DisplayName("FAILED 상태는 FAILED 코드와 사유 코드를 반환한다")
        void failedStatus_returnsFailedCodeWithReasonCode() throws Exception {
            when(bookingUsecase.getBookingStatus(anyLong(), anyString()))
                    .thenReturn(new BookingStatusResult.Failed("ERR_001", "결제 실패"));

            mockMvc.perform(statusRequest())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("FAILED"))
                    .andExpect(jsonPath("$.data.code").value("ERR_001"));
        }

        @Test
        @DisplayName("FAILED 응답은 사유 메시지를 포함한다")
        void failedStatus_containsMessage() throws Exception {
            when(bookingUsecase.getBookingStatus(anyLong(), anyString()))
                    .thenReturn(new BookingStatusResult.Failed("ERR_001", "결제 실패"));

            mockMvc.perform(statusRequest())
                    .andExpect(jsonPath("$.data.message").value("결제 실패"));
        }

        @Test
        @DisplayName("예약 결과를 찾을 수 없으면 NOT_FOUND 상태를 반환한다")
        void notFoundStatus_returnsNotFoundCode() throws Exception {
            when(bookingUsecase.getBookingStatus(anyLong(), anyString()))
                    .thenReturn(new BookingStatusResult.NotFound());

            mockMvc.perform(statusRequest())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("UNCERTAIN 상태는 UNCERTAIN 코드와 메시지를 반환한다")
        void uncertainStatus_returnsUncertainCodeWithMessage() throws Exception {
            when(bookingUsecase.getBookingStatus(anyLong(), anyString()))
                    .thenReturn(new BookingStatusResult.Uncertain("결제 확인 중"));

            mockMvc.perform(statusRequest())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("UNCERTAIN"))
                    .andExpect(jsonPath("$.data.message").value("결제 확인 중"));
        }
    }

    @Nested
    @DisplayName("예약 결과 조회 검증")
    class GetBookingStatusValidation {

        @Test
        @DisplayName("X-User-Id 헤더가 없으면 400을 반환한다")
        void missingUserIdHeader_returns400() throws Exception {
            mockMvc.perform(get("/api/v1/booking/status")
                            .param("productId", String.valueOf(PRODUCT_ID))
                            .param("ticketId", TICKET_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("productId 파라미터가 없으면 400을 반환한다")
        void missingProductId_returns400() throws Exception {
            mockMvc.perform(get("/api/v1/booking/status")
                            .header("X-User-Id", USER_ID)
                            .param("ticketId", TICKET_ID))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("ticketId 파라미터가 없으면 400을 반환한다")
        void missingTicketId_returns400() throws Exception {
            mockMvc.perform(get("/api/v1/booking/status")
                            .header("X-User-Id", USER_ID)
                            .param("productId", String.valueOf(PRODUCT_ID)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("productId가 숫자가 아니면 400을 반환한다")
        void nonNumericProductId_returns400() throws Exception {
            mockMvc.perform(get("/api/v1/booking/status")
                            .header("X-User-Id", USER_ID)
                            .param("productId", "abc")
                            .param("ticketId", TICKET_ID))
                    .andExpect(status().isBadRequest());
        }
    }
}