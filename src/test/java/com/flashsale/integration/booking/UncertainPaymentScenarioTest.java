package com.flashsale.integration.booking;

import com.flashsale.application.booking.dto.PaymentResult;
import com.flashsale.infrastructure.payment.gateway.CreditCardGateway;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

@DisplayName("불확실 결제 시나리오 통합 테스트")
class UncertainPaymentScenarioTest extends BookingTestBase {

    @MockitoSpyBean
    CreditCardGateway creditCardGateway;

    @BeforeEach
    void mockGateway() {
        doReturn(new PaymentResult.Unknown("uncertain-idem-pay")).when(creditCardGateway).charge(any());
    }

    @Test
    @DisplayName("Unknown 결제 결과는 UNCERTAIN 상태를 반환한다")
    void unknownPaymentResult_returnsUncertainStatus() {
        ResponseEntity<String> response = postBookingWithCreditCard(1L, "uncertain-1");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        String ticketId = extractTicketId(response);
        waitForFinalStatus(1L, ticketId);

        String status = pollStatus(1L, ticketId);
        assertThat(status).startsWith("UNCERTAIN");
    }

    @Test
    @DisplayName("Unknown 결제 결과는 조회 대기 큐에 PENDING 항목을 생성한다")
    void unknownPaymentResult_createsReconcileQueueEntry() {
        ResponseEntity<String> response = postBookingWithCreditCard(1L, "uncertain-queue-1");
        String ticketId = extractTicketId(response);

        waitForFinalStatus(1L, ticketId);

        Awaitility.await()
                .atMost(AWAIT_TIMEOUT)
                .pollInterval(AWAIT_POLL_INTERVAL)
                .until(() -> {
                    Integer count = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM payment_reconcile_queue WHERE status = 'PENDING'",
                            Integer.class
                    );
                    return count != null && count > 0;
                });

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_reconcile_queue WHERE status = 'PENDING'",
                Integer.class
        );
        assertThat(count).isGreaterThan(0);
    }
}
