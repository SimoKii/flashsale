package com.flashsale.integration.booking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("품절 시나리오 통합 테스트")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SoldOutScenarioTest extends BookingTestBase {

    @BeforeEach
    void setStockToOne() {
        redisTemplate.opsForValue().set("stock:product:" + PRODUCT_ID, "1");
        jdbcTemplate.update("UPDATE stock SET total = 1, sold = 0, reserved = 0 WHERE product_id = ?", PRODUCT_ID);
    }

    @Test
    @DisplayName("재고 1개에 두 사용자가 동시 요청하면 정확히 1명만 PAID가 된다")
    void concurrentRequests_onlyOneGetsPaid() throws Exception {
        CompletableFuture<ResponseEntity<String>> req1 =
                CompletableFuture.supplyAsync(() -> postBooking(1L, "sold-concurrent-1"));
        CompletableFuture<ResponseEntity<String>> req2 =
                CompletableFuture.supplyAsync(() -> postBooking(2L, "sold-concurrent-2"));

        ResponseEntity<String> res1 = req1.get(5, TimeUnit.SECONDS);
        ResponseEntity<String> res2 = req2.get(5, TimeUnit.SECONDS);

        assertThat(res1.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(res2.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        String ticket1 = extractTicketId(res1);
        String ticket2 = extractTicketId(res2);

        waitForFinalStatus(1L, ticket1);
        waitForFinalStatus(2L, ticket2);

        String status1 = pollStatus(1L, ticket1);
        String status2 = pollStatus(2L, ticket2);

        long paidCount = List.of(status1, status2).stream()
                .filter("PAID"::equals).count();
        long failedCount = List.of(status1, status2).stream()
                .filter(s -> s.startsWith("FAILED")).count();

        assertThat(paidCount).isEqualTo(1);
        assertThat(failedCount).isEqualTo(1);
    }

    @Test
    @DisplayName("품절 시 실패한 예약의 상태는 FAILED로 시작한다")
    void soldOut_failedStatusStartsWithFailed() throws Exception {
        CompletableFuture<ResponseEntity<String>> req1 =
                CompletableFuture.supplyAsync(() -> postBooking(1L, "sold-code-1"));
        CompletableFuture<ResponseEntity<String>> req2 =
                CompletableFuture.supplyAsync(() -> postBooking(2L, "sold-code-2"));

        String ticket1 = extractTicketId(req1.get(5, TimeUnit.SECONDS));
        String ticket2 = extractTicketId(req2.get(5, TimeUnit.SECONDS));

        waitForFinalStatus(1L, ticket1);
        waitForFinalStatus(2L, ticket2);

        String status1 = pollStatus(1L, ticket1);
        String status2 = pollStatus(2L, ticket2);

        String failedStatus = "PAID".equals(status1) ? status2 : status1;
        assertThat(failedStatus).startsWith("FAILED");
    }
}
