package com.flashsale.integration.checkout;

import com.flashsale.integration.IntegrationTestBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Checkout API 통합 테스트")
class CheckoutIntegrationTest extends IntegrationTestBase {

    private static final long EXISTING_PRODUCT_ID = 1L;
    private static final long EXISTING_USER_ID = 1L;
    private static final long EXISTING_USER_POINT_BALANCE = 100_000L;
    private static final int EXISTING_PRODUCT_STOCK = 10;
    private static final long UNKNOWN_PRODUCT_ID = 999_999L;

    @BeforeEach
    void resetCheckoutState() {
        truncateTables();
        jdbcTemplate.update("UPDATE stock SET sold = 0, reserved = 0 WHERE product_id = ?", EXISTING_PRODUCT_ID);
        jdbcTemplate.update("UPDATE point_account SET balance = 100000");
    }

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/checkout";
    }

    private ResponseEntity<String> get(
            final String url,
            final String userId
    ) {
        HttpHeaders headers = new HttpHeaders();
        if (userId != null) {
            headers.set("X-User-Id", userId);
        }
        return restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );
    }

    @Nested
    @DisplayName("정상 조회")
    class NormalCheckout {

        @Test
        @DisplayName("유효한 요청은 200과 SUCCESS 코드를 반환한다")
        void validRequest_returns200WithSuccessCode() {
            ResponseEntity<String> response = get(
                    baseUrl() + "?productId=" + EXISTING_PRODUCT_ID,
                    String.valueOf(EXISTING_USER_ID)
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(JsonPath.<String>read(response.getBody(), "$.code")).isEqualTo("SUCCESS");
        }

        @Test
        @DisplayName("응답에 상품 정보가 포함된다")
        void response_containsProductInfo() {
            ResponseEntity<String> response = get(
                    baseUrl() + "?productId=" + EXISTING_PRODUCT_ID,
                    String.valueOf(EXISTING_USER_ID)
            );

            String body = response.getBody();
            assertThat(JsonPath.<Integer>read(body, "$.data.product.id"))
                    .isEqualTo((int) EXISTING_PRODUCT_ID);
            assertThat(JsonPath.<String>read(body, "$.data.product.name"))
                    .isNotBlank();
            assertThat(JsonPath.<Integer>read(body, "$.data.product.price"))
                    .isPositive();
            assertThat(JsonPath.<String>read(body, "$.data.product.checkinAt"))
                    .isNotBlank();
            assertThat(JsonPath.<String>read(body, "$.data.product.checkoutAt"))
                    .isNotBlank();
            assertThat(JsonPath.<Boolean>read(body, "$.data.product.onSale"))
                    .isNotNull();
        }

        @Test
        @DisplayName("응답에 사용자 포인트 잔액이 포함된다")
        void response_containsUserPointBalance() {
            ResponseEntity<String> response = get(
                    baseUrl() + "?productId=" + EXISTING_PRODUCT_ID,
                    String.valueOf(EXISTING_USER_ID)
            );

            assertThat(JsonPath.<Integer>read(response.getBody(), "$.data.userPoint.balance"))
                    .isEqualTo((int) EXISTING_USER_POINT_BALANCE);
        }

        @Test
        @DisplayName("응답에 잔여 재고가 포함된다")
        void response_containsStockRemaining() {
            ResponseEntity<String> response = get(
                    baseUrl() + "?productId=" + EXISTING_PRODUCT_ID,
                    String.valueOf(EXISTING_USER_ID)
            );

            assertThat(JsonPath.<Integer>read(response.getBody(), "$.data.stock.remaining"))
                    .isEqualTo(EXISTING_PRODUCT_STOCK);
        }

        @Test
        @DisplayName("응답 Content-Type은 JSON이다")
        void response_contentTypeIsJson() {
            ResponseEntity<String> response = get(
                    baseUrl() + "?productId=" + EXISTING_PRODUCT_ID,
                    String.valueOf(EXISTING_USER_ID)
            );

            assertThat(response.getHeaders().getContentType().toString())
                    .contains("application/json");
        }
    }

    @Nested
    @DisplayName("존재하지 않는 상품")
    class UnknownProduct {

        @Test
        @DisplayName("존재하지 않는 상품 조회 시 400과 DOMAIN_ERROR를 반환한다")
        void unknownProduct_returns400WithDomainError() {
            ResponseEntity<String> response = get(
                    baseUrl() + "?productId=" + UNKNOWN_PRODUCT_ID,
                    String.valueOf(EXISTING_USER_ID)
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(JsonPath.<String>read(response.getBody(), "$.code"))
                    .isEqualTo("DOMAIN_ERROR");
            assertThat(JsonPath.<String>read(response.getBody(), "$.message"))
                    .isNotBlank();
        }
    }

    @Nested
    @DisplayName("요청 검증")
    class RequestValidation {

        @Test
        @DisplayName("X-User-Id 헤더 없이 요청 시 400과 MISSING_HEADER를 반환한다")
        void missingUserIdHeader_returns400WithMissingHeader() {
            ResponseEntity<String> response = get(
                    baseUrl() + "?productId=" + EXISTING_PRODUCT_ID,
                    null
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(JsonPath.<String>read(response.getBody(), "$.code"))
                    .isEqualTo("MISSING_HEADER");
        }

        @Test
        @DisplayName("productId 파라미터 없이 요청 시 400과 MISSING_PARAMETER를 반환한다")
        void missingProductId_returns400WithMissingParameter() {
            ResponseEntity<String> response = get(
                    baseUrl(),
                    String.valueOf(EXISTING_USER_ID)
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(JsonPath.<String>read(response.getBody(), "$.code"))
                    .isEqualTo("MISSING_PARAMETER");
        }

        @Test
        @DisplayName("productId가 숫자가 아니면 400을 반환한다")
        void nonNumericProductId_returns400() {
            ResponseEntity<String> response = get(
                    baseUrl() + "?productId=abc",
                    String.valueOf(EXISTING_USER_ID)
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("X-User-Id가 숫자가 아니면 400을 반환한다")
        void nonNumericUserId_returns400() {
            ResponseEntity<String> response = get(
                    baseUrl() + "?productId=" + EXISTING_PRODUCT_ID,
                    "abc"
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
