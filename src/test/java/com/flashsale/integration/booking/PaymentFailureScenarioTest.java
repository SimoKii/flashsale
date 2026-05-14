package com.flashsale.integration.booking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("결제 실패 시나리오 통합 테스트")
class PaymentFailureScenarioTest extends BookingTestBase {

    @BeforeEach
    void zeroBalance() {
        jdbcTemplate.update("UPDATE point_account SET balance = 0 WHERE user_id = 3");
    }

    @Test
    @DisplayName("잔액이 부족하면 예약이 FAILED 상태가 된다")
    void insufficientBalance_bookingFails() {
        ResponseEntity<String> response = postBooking(3L, "pay-fail-1");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        String ticketId = extractTicketId(response);
        waitForFinalStatus(3L, ticketId);

        String status = pollStatus(3L, ticketId);
        assertThat(status).startsWith("FAILED");
    }

    @Test
    @DisplayName("결제 실패 후 재고는 원복된다")
    void afterPaymentFailure_stockIsRestored() {
        ResponseEntity<String> response = postBooking(3L, "pay-fail-stock-1");
        String ticketId = extractTicketId(response);

        waitForFinalStatus(3L, ticketId);

        String stockStr = redisTemplate.opsForValue().get("stock:product:" + PRODUCT_ID);
        assertThat(Integer.parseInt(stockStr)).isEqualTo(INITIAL_STOCK);
    }
}
