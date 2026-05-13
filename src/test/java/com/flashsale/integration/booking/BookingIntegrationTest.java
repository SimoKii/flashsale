package com.flashsale.integration.booking;

import com.jayway.jsonpath.JsonPath;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@DisplayName("Booking API 통합 테스트")
class BookingIntegrationTest {

    private static final long PRODUCT_ID = 1L;
    private static final String STREAM_KEY = "queue:product:1";
    private static final String CONSUMER_GROUP = "booking-worker";
    private static final int INITIAL_STOCK = 10;
    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration AWAIT_POLL_INTERVAL = Duration.ofMillis(500);

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("flashsale")
            .withUsername("flashsale")
            .withPassword("flashsalepw");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(
            final DynamicPropertyRegistry registry
    ) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("flashsale.booking-worker.product-ids", () -> "1");
        registry.add("spring.data.redis.timeout", () -> "2000ms");
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetState() {
        redisTemplate.execute((RedisCallback<Object>) conn -> {
            conn.serverCommands().flushDb();
            return null;
        });

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

        jdbcTemplate.update("SET FOREIGN_KEY_CHECKS = 0");
        jdbcTemplate.update("TRUNCATE TABLE payment_event");
        jdbcTemplate.update("TRUNCATE TABLE payment_reconcile_queue");
        jdbcTemplate.update("TRUNCATE TABLE payment_line");
        jdbcTemplate.update("TRUNCATE TABLE point_tx");
        jdbcTemplate.update("TRUNCATE TABLE orders");
        jdbcTemplate.update("SET FOREIGN_KEY_CHECKS = 1");
        jdbcTemplate.update("UPDATE stock SET sold = 0, reserved = 0 WHERE product_id = ?", PRODUCT_ID);
        jdbcTemplate.update("UPDATE point_account SET balance = 100000");
    }

    private String bookingUrl() {
        return "http://localhost:" + port + "/api/v1/booking";
    }

    private String statusUrl() {
        return "http://localhost:" + port + "/api/v1/booking/status";
    }

    private HttpHeaders userHeader(
            final long userId
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", String.valueOf(userId));
        return headers;
    }

    private ResponseEntity<String> postBooking(
            final long userId,
            final String idemKey
    ) {
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

    private ResponseEntity<String> getStatus(
            final long userId,
            final String ticketId
    ) {
        return restTemplate.exchange(
                statusUrl() + "?productId=" + PRODUCT_ID + "&ticketId=" + ticketId,
                HttpMethod.GET,
                new HttpEntity<>(userHeader(userId)),
                String.class
        );
    }

    private String pollStatus(
            final long userId,
            final String ticketId
    ) {
        return JsonPath.read(getStatus(userId, ticketId).getBody(), "$.data.status");
    }

    private void waitForFinalStatus(
            final long userId,
            final String ticketId
    ) {
        Awaitility.await()
                .atMost(AWAIT_TIMEOUT)
                .pollDelay(AWAIT_POLL_INTERVAL)
                .pollInterval(AWAIT_POLL_INTERVAL)
                .until(() -> {
                    String status = pollStatus(userId, ticketId);
                    return !"PENDING".equals(status) && !"NOT_FOUND".equals(status);
                });
    }

    private String extractTicketId(
            final ResponseEntity<String> response
    ) {
        return JsonPath.read(response.getBody(), "$.data.ticketId");
    }

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
