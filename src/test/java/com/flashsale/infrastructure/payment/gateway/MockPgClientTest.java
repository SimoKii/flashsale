package com.flashsale.infrastructure.payment.gateway;

import com.flashsale.application.booking.dto.PaymentResult;
import com.flashsale.infrastructure.payment.client.MockPgClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MockPgClient")
class MockPgClientTest {

    @Nested
    @DisplayName("결제 요청")
    class Charge {

        @Test
        @DisplayName("성공률 100%로 설정하면 항상 성공을 반환한다")
        void successRate100_alwaysReturnsSuccess() {
            MockPgClient client = clientWith(
                    1.0,
                    0.0,
                    1L
            );

            PaymentResult result = client.charge("idem-key-001");

            assertThat(result).isInstanceOf(PaymentResult.Success.class);
        }

        @Test
        @DisplayName("실패율 100%로 설정하면 항상 실패를 반환한다")
        void failureRate100_alwaysReturnsFailure() {
            MockPgClient client = clientWith(
                    0.0,
                    1.0,
                    1L
            );

            PaymentResult result = client.charge("idem-key-002");

            assertThat(result).isInstanceOf(PaymentResult.Failure.class);
        }

        @Test
        @DisplayName("성공률과 실패율을 모두 0으로 설정하면 항상 불확실을 반환한다")
        void allZero_alwaysReturnsUnknown() {
            MockPgClient client = clientWith(
                    0.0,
                    0.0,
                    1L
            );

            PaymentResult result = client.charge("idem-key-003");

            assertThat(result).isInstanceOf(PaymentResult.Unknown.class);
        }

        @Test
        @DisplayName("성공 결과는 PG 거래 ID를 포함한다")
        void success_containsPgTxId() {
            MockPgClient client = clientWith(
                    1.0,
                    0.0,
                    1L
            );

            PaymentResult result = client.charge("idem-key-004");

            assertThat(result).isInstanceOf(PaymentResult.Success.class);
            PaymentResult.Success success = (PaymentResult.Success) result;
            assertThat(success.pgTxId()).isNotBlank();
        }

        @Test
        @DisplayName("실패 결과는 사유 코드를 포함한다")
        void failure_containsReasonCode() {
            MockPgClient client = clientWith(
                    0.0,
                    1.0,
                    1L
            );

            PaymentResult result = client.charge("idem-key-005");

            assertThat(result).isInstanceOf(PaymentResult.Failure.class);
            PaymentResult.Failure failure = (PaymentResult.Failure) result;
            assertThat(failure.reasonCode()).isNotBlank();
        }

        @Test
        @DisplayName("불확실 결과는 멱등성 키를 포함한다")
        void unknown_containsIdempotencyKey() {
            MockPgClient client = clientWith(
                    0.0,
                    0.0,
                    1L
            );

            PaymentResult result = client.charge("idem-key-006");

            assertThat(result).isInstanceOf(PaymentResult.Unknown.class);
            PaymentResult.Unknown unknown = (PaymentResult.Unknown) result;
            assertThat(unknown.idempotencyKey()).isEqualTo("idem-key-006");
        }

        @Test
        @DisplayName("동일한 시드로 두 번 호출하면 동일한 결과를 반환한다")
        void sameSeed_returnsDeterministicResult() {
            MockPgClient client1 = clientWith(
                    0.5,
                    0.3,
                    42L
            );
            MockPgClient client2 = clientWith(
                    0.5,
                    0.3,
                    42L
            );

            PaymentResult result1 = client1.charge("idem-key-007");
            PaymentResult result2 = client2.charge("idem-key-007");

            assertThat(result1.getClass()).isEqualTo(result2.getClass());
        }
    }

    @Nested
    @DisplayName("실제 비율 검증")
    class Distribution {

        @Test
        @DisplayName("성공률 95%와 실패율 3%로 1000회 호출하면 분포가 근사하게 일치한다")
        void chargeDistribution_matchesConfiguredRates() {
            MockPgClient client = clientWith(
                    0.95,
                    0.03,
                    123L
            );

            int successCount = 0;
            int failureCount = 0;
            int unknownCount = 0;
            int iterations = 1000;

            for (int i = 0; i < iterations; i++) {
                PaymentResult result = client.charge("idem-key-dist-" + i);
                if (result instanceof PaymentResult.Success) {
                    successCount++;
                } else if (result instanceof PaymentResult.Failure) {
                    failureCount++;
                } else {
                    unknownCount++;
                }
            }

            assertThat(successCount).isBetween(900, 1000);
            assertThat(failureCount).isBetween(0, 80);
            assertThat(unknownCount).isBetween(0, 80);
            assertThat(successCount + failureCount + unknownCount).isEqualTo(iterations);
        }
    }

    private MockPgClient clientWith(
            final double successRate,
            final double failureRate,
            final long seed
    ) {
        MockPgClient client = new MockPgClient();
        ReflectionTestUtils.setField(client, "successRate", successRate);
        ReflectionTestUtils.setField(client, "failureRate", failureRate);
        ReflectionTestUtils.setField(client, "seed", seed);
        ReflectionTestUtils.invokeMethod(client, "init");
        return client;
    }
}