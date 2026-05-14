package com.flashsale.integration.booking;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("멱등성 동시성 시나리오 통합 테스트")
class IdempotencyScenarioTest extends BookingTestBase {

    @Test
    @DisplayName("동일 멱등키로 10개 동시 요청 시 202 응답은 모두 같은 ticketId를 반환한다")
    void concurrentSameKey_allAcceptedReturnSameTicketId() throws Exception {
        int threadCount = 10;
        String sharedIdemKey = "idem-concurrent-key";
        long userId = 1L;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Callable<ResponseEntity<String>>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> postBooking(userId, sharedIdemKey));
        }

        List<Future<ResponseEntity<String>>> futures = executor.invokeAll(tasks, 10, TimeUnit.SECONDS);
        executor.shutdown();

        List<ResponseEntity<String>> responses = new ArrayList<>();
        for (Future<ResponseEntity<String>> f : futures) {
            responses.add(f.get());
        }

        long acceptedCount = responses.stream().filter(r -> r.getStatusCode().value() == 202).count();
        long conflictCount = responses.stream().filter(r -> r.getStatusCode().value() == 409).count();

        assertThat(acceptedCount).isGreaterThanOrEqualTo(1);
        assertThat(acceptedCount + conflictCount).isEqualTo(threadCount);

        Set<String> ticketIds = responses.stream()
                .filter(r -> r.getStatusCode().value() == 202)
                .map(r -> JsonPath.<String>read(r.getBody(), "$.data.ticketId"))
                .collect(Collectors.toSet());

        assertThat(ticketIds).hasSize(1);
    }

    @Test
    @DisplayName("동일 멱등키로 10개 동시 요청 후 DB에 주문은 1개만 생성된다")
    void concurrentSameKey_onlyOneOrderInDb() throws Exception {
        int threadCount = 10;
        String sharedIdemKey = "idem-db-check-key";
        long userId = 1L;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Callable<ResponseEntity<String>>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> postBooking(userId, sharedIdemKey));
        }

        List<Future<ResponseEntity<String>>> futures = executor.invokeAll(tasks, 10, TimeUnit.SECONDS);
        executor.shutdown();

        String acceptedTicketId = null;
        for (Future<ResponseEntity<String>> f : futures) {
            ResponseEntity<String> res = f.get();
            if (res.getStatusCode() == HttpStatus.ACCEPTED) {
                acceptedTicketId = extractTicketId(res);
            }
        }

        assertThat(acceptedTicketId).isNotNull();
        waitForFinalStatus(userId, acceptedTicketId);

        Integer orderCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE idempotency_key = ?",
                Integer.class,
                sharedIdemKey
        );
        assertThat(orderCount).isEqualTo(1);
    }
}
