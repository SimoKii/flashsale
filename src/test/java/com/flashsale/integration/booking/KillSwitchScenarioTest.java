package com.flashsale.integration.booking;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("킬스위치 시나리오 통합 테스트")
class KillSwitchScenarioTest extends BookingTestBase {

    private static final String KILL_SWITCH_KEY = "kill_switch:product:" + PRODUCT_ID;
    private static final String DLQ_KEY = "dlq:product:" + PRODUCT_ID;

    @Test
    @DisplayName("킬스위치 ON 상태에서 요청은 DLQ로 보내진다")
    void killSwitchOn_requestGoesToDlq() {
        redisTemplate.opsForValue().set(KILL_SWITCH_KEY, "test-reason", Duration.ofMinutes(5));

        ResponseEntity<String> resA = postBooking(1L, "kill-on-req-a");
        assertThat(resA.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        Awaitility.await()
                .atMost(AWAIT_TIMEOUT)
                .pollInterval(AWAIT_POLL_INTERVAL)
                .until(() -> {
                    Long size = redisTemplate.opsForStream().size(DLQ_KEY);
                    return size != null && size > 0;
                });

        assertThat(redisTemplate.opsForStream().size(DLQ_KEY)).isGreaterThan(0);
        assertThat(pollStatus(1L, extractTicketId(resA))).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("킬스위치 OFF 후 새 요청은 정상 처리되어 PAID가 된다")
    void killSwitchOff_newRequestGetsPaid() {
        redisTemplate.delete(KILL_SWITCH_KEY);

        ResponseEntity<String> resB = postBooking(1L, "kill-off-req-b");
        assertThat(resB.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        String ticketB = extractTicketId(resB);
        waitForFinalStatus(1L, ticketB);

        assertThat(pollStatus(1L, ticketB)).isEqualTo("PAID");
    }
}
