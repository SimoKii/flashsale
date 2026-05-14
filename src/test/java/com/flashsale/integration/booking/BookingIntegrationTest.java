package com.flashsale.integration.booking;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Booking API 통합 테스트")
class BookingIntegrationTest extends BookingTestBase {

    @Nested
    @DisplayName("예약 성공 흐름")
    class BookingSuccessFlow {

        @Test
        @DisplayName("예약 요청은 202와 ticketId를 반환한다")
        void newBooking_returns202WithTicketId() {
            ResponseEntity<String> response = postBooking(1L, "it-success-1");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            assertThat(extractTicketId(response)).isNotBlank();
        }

        @Test
        @DisplayName("예약 요청 후 처리 완료되면 상태가 PAID가 된다")
        void booking_afterProcessing_statusBecomesPaid() {
            ResponseEntity<String> response = postBooking(1L, "it-success-2");
            String ticketId = extractTicketId(response);

            waitForFinalStatus(1L, ticketId);

            String statusBody = getStatus(1L, ticketId).getBody();
            assertThat(JsonPath.<String>read(statusBody, "$.data.status")).isEqualTo("PAID");
        }

        @Test
        @DisplayName("PAID 응답에는 주문 ID가 포함된다")
        void paidResponse_containsOrderId() {
            ResponseEntity<String> response = postBooking(1L, "it-success-3");
            String ticketId = extractTicketId(response);

            waitForFinalStatus(1L, ticketId);

            String statusBody = getStatus(1L, ticketId).getBody();
            assertThat(JsonPath.<Integer>read(statusBody, "$.data.orderId")).isPositive();
        }
    }

    @Nested
    @DisplayName("멱등성")
    class Idempotency {

        @Test
        @DisplayName("처리 완료 후 동일 멱등키로 재요청 시 동일한 ticketId를 반환한다")
        void afterProcessing_sameKey_returnsSameTicketId() {
            ResponseEntity<String> first = postBooking(1L, "it-idem-1");
            String originalTicketId = extractTicketId(first);

            waitForFinalStatus(1L, originalTicketId);

            ResponseEntity<String> second = postBooking(1L, "it-idem-1");

            assertThat(second.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            assertThat(extractTicketId(second)).isEqualTo(originalTicketId);
        }

        @Test
        @DisplayName("처리 완료 후 동일 멱등키 재요청은 새 주문을 생성하지 않는다")
        void afterProcessing_sameKey_doesNotCreateNewOrder() {
            ResponseEntity<String> first = postBooking(1L, "it-idem-2");
            String originalTicketId = extractTicketId(first);

            waitForFinalStatus(1L, originalTicketId);
            Integer originalOrderId = JsonPath.read(
                    getStatus(1L, originalTicketId).getBody(),
                    "$.data.orderId"
            );

            postBooking(1L, "it-idem-2");

            Integer reissuedOrderId = JsonPath.read(
                    getStatus(1L, originalTicketId).getBody(),
                    "$.data.orderId"
            );
            assertThat(reissuedOrderId).isEqualTo(originalOrderId);
        }
    }

    @Nested
    @DisplayName("중복 사용자 가드")
    class DuplicateUserGuard {

        @Test
        @DisplayName("처리 중인 예약이 있을 때 동일 사용자의 다른 멱등키 요청은 409를 반환한다")
        void inFlight_differentKey_returns409() {
            ResponseEntity<String> first = postBooking(1L, "it-dup-1a");
            assertThat(first.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

            ResponseEntity<String> second = postBooking(1L, "it-dup-1b");

            assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("중복 사용자 응답은 DUPLICATE_BOOKING 코드를 반환한다")
        void inFlight_differentKey_returnsDuplicateBookingCode() {
            postBooking(1L, "it-dup-2a");

            ResponseEntity<String> second = postBooking(1L, "it-dup-2b");

            assertThat(JsonPath.<String>read(second.getBody(), "$.code"))
                    .isEqualTo("DUPLICATE_BOOKING");
        }

        @Test
        @DisplayName("예약 처리 완료 후 동일 사용자의 새 예약 요청은 다른 멱등키로 가능하다")
        void afterProcessing_differentKey_isAccepted() {
            ResponseEntity<String> first = postBooking(1L, "it-dup-3a");
            String ticketId = extractTicketId(first);

            waitForFinalStatus(1L, ticketId);

            ResponseEntity<String> second = postBooking(1L, "it-dup-3b");

            assertThat(second.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        }
    }

    @Nested
    @DisplayName("응답 본문 형식")
    class ResponseFormat {

        @Test
        @DisplayName("예약 응답에는 ticketId와 queuePosition이 포함된다")
        void acceptedResponse_containsTicketIdAndQueuePosition() {
            ResponseEntity<String> response = postBooking(1L, "it-format-1");

            assertThat(JsonPath.<String>read(response.getBody(), "$.data.ticketId"))
                    .isNotBlank();
            assertThat(JsonPath.<Integer>read(response.getBody(), "$.data.queuePosition"))
                    .isNotNull();
        }

        @Test
        @DisplayName("예약 응답은 SUCCESS 코드를 포함한다")
        void acceptedResponse_containsSuccessCode() {
            ResponseEntity<String> response = postBooking(1L, "it-format-2");

            assertThat(JsonPath.<String>read(response.getBody(), "$.code"))
                    .isEqualTo("SUCCESS");
        }
    }

    @Nested
    @DisplayName("상태 조회 검증")
    class StatusValidation {

        @Test
        @DisplayName("X-User-Id 헤더가 없으면 400을 반환한다")
        void missingUserIdHeader_returns400() {
            ResponseEntity<String> response = restTemplate.exchange(
                    statusUrl() + "?productId=" + PRODUCT_ID + "&ticketId=any",
                    HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("productId 파라미터가 없으면 400을 반환한다")
        void missingProductId_returns400() {
            ResponseEntity<String> response = restTemplate.exchange(
                    statusUrl() + "?ticketId=any",
                    HttpMethod.GET,
                    new HttpEntity<>(userHeader(1L)),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("ticketId 파라미터가 없으면 400을 반환한다")
        void missingTicketId_returns400() {
            ResponseEntity<String> response = restTemplate.exchange(
                    statusUrl() + "?productId=" + PRODUCT_ID,
                    HttpMethod.GET,
                    new HttpEntity<>(userHeader(1L)),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
