package com.flashsale.interfaces.common;

import com.flashsale.application.booking.exception.DuplicateBookingException;
import com.flashsale.application.booking.exception.InvalidPaymentCompositionException;
import com.flashsale.application.booking.exception.ServiceUnavailableException;
import com.flashsale.common.exception.DomainException;
import com.flashsale.domain.stock.StockExhaustedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {GlobalExceptionHandlerTest.TestController.class, GlobalExceptionHandler.class})
@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @RestController
    static class TestController {

        @GetMapping("/test/sold-out")
        public void soldOut() {
            throw new StockExhaustedException(1L);
        }

        @GetMapping("/test/duplicate")
        public void duplicate() {
            throw new DuplicateBookingException("이미 진행 중인 예약");
        }

        @GetMapping("/test/unavailable")
        public void unavailable() {
            throw new ServiceUnavailableException("일시적 점검");
        }

        @GetMapping("/test/invalid-composition")
        public void invalidComposition() {
            throw new InvalidPaymentCompositionException("카드와 Y페이는 혼용 불가");
        }

        @GetMapping("/test/domain-error")
        public void domainError() {
            throw new DomainException("도메인 규칙 위반");
        }

        @GetMapping("/test/unexpected")
        public void unexpected() {
            throw new RuntimeException("예상하지 못한 오류");
        }
    }

    @Nested
    @DisplayName("도메인 예외 처리")
    class DomainExceptions {

        @Test
        @DisplayName("재고 소진 예외는 409와 SOLD_OUT 코드를 반환한다")
        void stockExhausted_returns409WithSoldOutCode() throws Exception {
            mockMvc.perform(get("/test/sold-out"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("SOLD_OUT"))
                    .andExpect(jsonPath("$.message").value(notNullValue()));
        }

        @Test
        @DisplayName("일반 도메인 예외는 400과 DOMAIN_ERROR 코드를 반환한다")
        void domainException_returns400WithDomainErrorCode() throws Exception {
            mockMvc.perform(get("/test/domain-error"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("DOMAIN_ERROR"))
                    .andExpect(jsonPath("$.message").value("도메인 규칙 위반"));
        }
    }

    @Nested
    @DisplayName("예약 예외 처리")
    class BookingExceptions {

        @Test
        @DisplayName("중복 예약 예외는 409와 DUPLICATE_BOOKING 코드를 반환한다")
        void duplicateBooking_returns409WithDuplicateBookingCode() throws Exception {
            mockMvc.perform(get("/test/duplicate"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DUPLICATE_BOOKING"))
                    .andExpect(jsonPath("$.message").value("이미 진행 중인 예약"));
        }

        @Test
        @DisplayName("결제 조합 오류 예외는 400과 INVALID_PAYMENT_COMPOSITION 코드를 반환한다")
        void invalidPaymentComposition_returns400WithCompositionCode() throws Exception {
            mockMvc.perform(get("/test/invalid-composition"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_COMPOSITION"))
                    .andExpect(jsonPath("$.message").value("카드와 Y페이는 혼용 불가"));
        }
    }

    @Nested
    @DisplayName("서비스 가용성 예외 처리")
    class ServiceAvailability {

        @Test
        @DisplayName("서비스 불가 예외는 503과 SERVICE_UNAVAILABLE 코드를 반환한다")
        void serviceUnavailable_returns503WithUnavailableCode() throws Exception {
            mockMvc.perform(get("/test/unavailable"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                    .andExpect(jsonPath("$.message").value("일시적 점검"));
        }

        @Test
        @DisplayName("서비스 불가 예외는 Retry-After 헤더를 포함한다")
        void serviceUnavailable_includesRetryAfterHeader() throws Exception {
            mockMvc.perform(get("/test/unavailable"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().exists("Retry-After"));
        }
    }

    @Nested
    @DisplayName("예상하지 못한 예외 처리")
    class UnexpectedExceptions {

        @Test
        @DisplayName("처리되지 않은 예외는 500과 INTERNAL_ERROR 코드를 반환한다")
        void unexpectedException_returns500WithInternalErrorCode() throws Exception {
            mockMvc.perform(get("/test/unexpected"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        }

        @Test
        @DisplayName("처리되지 않은 예외의 응답에는 메시지가 포함된다")
        void unexpectedException_includesUserFriendlyMessage() throws Exception {
            mockMvc.perform(get("/test/unexpected"))
                    .andExpect(jsonPath("$.message").value(notNullValue()))
                    .andExpect(jsonPath("$.code").exists());
        }
    }

    @Nested
    @DisplayName("응답 형식 일관성")
    class ResponseFormat {

        @Test
        @DisplayName("모든 예외 응답은 code와 message 필드를 포함한다")
        void allErrorResponses_haveCodeAndMessage() throws Exception {
            mockMvc.perform(get("/test/sold-out"))
                    .andExpect(jsonPath("$.code").exists())
                    .andExpect(jsonPath("$.message").exists());

            mockMvc.perform(get("/test/duplicate"))
                    .andExpect(jsonPath("$.code").exists())
                    .andExpect(jsonPath("$.message").exists());

            mockMvc.perform(get("/test/unavailable"))
                    .andExpect(jsonPath("$.code").exists())
                    .andExpect(jsonPath("$.message").exists());
        }

        @Test
        @DisplayName("오류 응답의 data 필드는 null이다")
        void errorResponse_dataFieldIsNull() throws Exception {
            mockMvc.perform(get("/test/sold-out"))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }
    }
}