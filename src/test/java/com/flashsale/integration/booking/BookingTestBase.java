package com.flashsale.integration.booking;

import com.flashsale.integration.IntegrationTestBase;
import com.jayway.jsonpath.JsonPath;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.Duration;

public abstract class BookingTestBase extends IntegrationTestBase {

    protected static final long PRODUCT_ID = 1L;
    protected static final String STREAM_KEY = "queue:product:1";
    protected static final String CONSUMER_GROUP = "booking-worker";
    protected static final int INITIAL_STOCK = 10;
    protected static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(10);
    protected static final Duration AWAIT_POLL_INTERVAL = Duration.ofMillis(500);

    @BeforeEach
    void resetState() {
        flushRedis();

        redisTemplate.opsForValue().set("stock:product:" + PRODUCT_ID, String.valueOf(INITIAL_STOCK));

        redisTemplate.execute((RedisCallback<Object>) conn -> {
            try {
                conn.execute("XGROUP",
                        "CREATE".getBytes(),
                        STREAM_KEY.getBytes(),
                        CONSUMER_GROUP.getBytes(),
                        "$".getBytes(),
                        "MKSTREAM".getBytes());
            } catch (Exception ignored) {
            }
            return null;
        });

        truncateTables();
        jdbcTemplate.update("UPDATE stock SET total = " + INITIAL_STOCK + ", sold = 0, reserved = 0 WHERE product_id = ?", PRODUCT_ID);
        jdbcTemplate.update("UPDATE point_account SET balance = 100000");
    }

    protected String bookingUrl() {
        return "http://localhost:" + port + "/api/v1/booking";
    }

    protected String statusUrl() {
        return "http://localhost:" + port + "/api/v1/booking/status";
    }

    protected HttpHeaders userHeader(final long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", String.valueOf(userId));
        return headers;
    }

    protected ResponseEntity<String> postBooking(final long userId, final String idemKey) {
        HttpHeaders headers = userHeader(userId);
        headers.set("Idempotency-Key", idemKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        String body = """
                {
                  "productId": 1,
                  "totalAmount": 50000,
                  "paymentLines": [
                    {
                      "sequence": 1,
                      "method": "YPOINT",
                      "amount": 50000,
                      "idempotencyKey": "%s-pay"
                    }
                  ]
                }
                """.formatted(idemKey);

        return restTemplate.postForEntity(
                bookingUrl(),
                new HttpEntity<>(body, headers),
                String.class
        );
    }

    protected ResponseEntity<String> postBookingWithCreditCard(final long userId, final String idemKey) {
        HttpHeaders headers = userHeader(userId);
        headers.set("Idempotency-Key", idemKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        String body = """
                {
                  "productId": 1,
                  "totalAmount": 50000,
                  "paymentLines": [
                    {
                      "sequence": 1,
                      "method": "CREDIT_CARD",
                      "amount": 50000,
                      "idempotencyKey": "%s-pay"
                    }
                  ]
                }
                """.formatted(idemKey);

        return restTemplate.postForEntity(
                bookingUrl(),
                new HttpEntity<>(body, headers),
                String.class
        );
    }

    protected ResponseEntity<String> getStatus(final long userId, final String ticketId) {
        return restTemplate.exchange(
                statusUrl() + "?productId=" + PRODUCT_ID + "&ticketId=" + ticketId,
                HttpMethod.GET,
                new HttpEntity<>(userHeader(userId)),
                String.class
        );
    }

    protected String pollStatus(final long userId, final String ticketId) {
        return JsonPath.read(getStatus(userId, ticketId).getBody(), "$.data.status");
    }

    protected void waitForFinalStatus(final long userId, final String ticketId) {
        Awaitility.await()
                .atMost(AWAIT_TIMEOUT)
                .pollDelay(AWAIT_POLL_INTERVAL)
                .pollInterval(AWAIT_POLL_INTERVAL)
                .until(() -> {
                    String status = pollStatus(userId, ticketId);
                    return !"PENDING".equals(status) && !"NOT_FOUND".equals(status);
                });
    }

    protected String extractTicketId(final ResponseEntity<String> response) {
        return JsonPath.read(response.getBody(), "$.data.ticketId");
    }
}
