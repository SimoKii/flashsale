package com.flashsale.domain.order;

import com.flashsale.common.exception.DomainException;
import com.flashsale.domain.shared.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Order")
class OrderTest {

    private static final IdempotencyKey IDEM_KEY = IdempotencyKey.of("order-key-12345678");
    private static final Money TOTAL = Money.of(50000);
    private static final LocalDateTime EXPIRES_AT = LocalDateTime.now().plusMinutes(5);

    private Order order;

    @BeforeEach
    void setUp() {
        order = Order.create(
                IDEM_KEY,
                1L,
                1L,
                TOTAL,
                EXPIRES_AT
        );
    }

    @Nested
    @DisplayName("생성")
    class Create {

        @Test
        @DisplayName("신규 주문을 생성하면 대기 상태이고 ID가 없다")
        void newOrder_isPendingAndHasNoId() {
            assertThat(order.getId()).isNull();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(order.getIdempotencyKey()).isEqualTo(IDEM_KEY);
            assertThat(order.getUserId()).isEqualTo(1L);
            assertThat(order.getProductId()).isEqualTo(1L);
            assertThat(order.getTotalAmount()).isEqualTo(TOTAL);
            assertThat(order.getExpiresAt()).isEqualTo(EXPIRES_AT);
            assertThat(order.getResponseBody()).isNull();
            assertThat(order.getPaymentLines()).isEmpty();
            assertThat(order.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("기존 주문을 복원하면 저장된 상태와 결제 라인이 유지된다")
        void existingOrder_preservesStateAndPaymentLines() {
            LocalDateTime savedTime = LocalDateTime.of(2026, 5, 1, 0, 0);
            List<PaymentLine> savedLines = List.of(
                    PaymentLine.restore(
                            1L,
                            PaymentMethodCode.YPOINT,
                            Money.of(10000),
                            PaymentLineStatus.APPROVED,
                            "point-tx-1",
                            1),
                    PaymentLine.restore(
                            2L,
                            PaymentMethodCode.CREDIT_CARD,
                            Money.of(40000),
                            PaymentLineStatus.APPROVED,
                            "card-tx-1",
                            2
                    )
            );

            Order restored = Order.restore(
                    100L,
                    IDEM_KEY,
                    1L,
                    1L,
                    TOTAL,
                    OrderStatus.PAID,
                    EXPIRES_AT,
                    "response",
                    savedLines,
                    savedTime
            );

            assertThat(restored.getId()).isEqualTo(100L);
            assertThat(restored.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(restored.getResponseBody()).isEqualTo("response");
            assertThat(restored.getCreatedAt()).isEqualTo(savedTime);
            assertThat(restored.getPaymentLines()).hasSize(2);
        }

        @Test
        @DisplayName("멱등성 키가 null이면 예외가 발생한다")
        void nullIdempotencyKey_throwsException() {
            assertThatThrownBy(() -> Order.create(
                    null,
                    1L,
                    1L,
                    TOTAL,
                    EXPIRES_AT
            )).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("총액이 null이면 예외가 발생한다")
        void nullTotalAmount_throwsException() {
            assertThatThrownBy(() -> Order.create(
                    IDEM_KEY,
                    1L,
                    1L,
                    null,
                    EXPIRES_AT
            )).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("만료 일시가 null이면 예외가 발생한다")
        void nullExpiresAt_throwsException() {
            assertThatThrownBy(() -> Order.create(
                    IDEM_KEY,
                    1L,
                    1L,
                    TOTAL,
                    null
            )).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("양수가 아닌 사용자 ID로 생성하면 예외가 발생한다")
        void nonPositiveUserId_throwsException() {
            assertThatThrownBy(() -> Order.create(
                    IDEM_KEY,
                    0L,
                    1L,
                    TOTAL,
                    EXPIRES_AT
            )).isInstanceOf(DomainException.class).hasMessageContaining("userId");
        }

        @Test
        @DisplayName("양수가 아닌 상품 ID로 생성하면 예외가 발생한다")
        void nonPositiveProductId_throwsException() {
            assertThatThrownBy(() -> Order.create(
                    IDEM_KEY,
                    1L,
                    0L,
                    TOTAL,
                    EXPIRES_AT
            )).isInstanceOf(DomainException.class).hasMessageContaining("productId");
        }
    }

    @Nested
    @DisplayName("상태 전이")
    class StatusTransition {

        @Nested
        @DisplayName("허용되는 전이")
        class Allowed {

            @Test
            @DisplayName("대기 상태에서 결제 완료로 전이할 수 있다")
            void pendingToPaid() {
                order.markPaid("response");

                assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
                assertThat(order.getResponseBody()).isEqualTo("response");
            }

            @Test
            @DisplayName("대기 상태에서 실패로 전이할 수 있다")
            void pendingToFailed() {
                order.markFailed("fail");

                assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
                assertThat(order.getResponseBody()).isEqualTo("fail");
            }

            @Test
            @DisplayName("보상 중 상태에서 실패로 전이할 수 있다")
            void compensatingToFailed() {
                order.markCompensating();

                order.markFailed("fail");

                assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
            }

            @Test
            @DisplayName("대기 상태에서 불확실로 전이할 수 있다")
            void pendingToUncertain() {
                order.markUncertain("uncertain");

                assertThat(order.getStatus()).isEqualTo(OrderStatus.UNCERTAIN);
                assertThat(order.getResponseBody()).isEqualTo("uncertain");
            }

            @Test
            @DisplayName("대기 상태에서 보상 중으로 전이할 수 있다")
            void pendingToCompensating() {
                order.markCompensating();

                assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPENSATING);
            }

            @Test
            @DisplayName("보상 중 상태에서 취소로 전이할 수 있다")
            void compensatingToCanceled() {
                order.markCompensating();

                order.markCanceled();

                assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
            }
        }

        @Nested
        @DisplayName("거부되는 전이")
        class Rejected {

            @Test
            @DisplayName("이미 결제 완료된 주문은 다시 결제 완료로 전이할 수 없다")
            void alreadyPaid_throwsException() {
                order.markPaid("response");

                assertThatThrownBy(() -> order.markPaid("response2"))
                        .isInstanceOf(DomainException.class).hasMessageContaining("PAID");
            }

            @Test
            @DisplayName("실패 상태에서 결제 완료로 전이할 수 없다")
            void failedToPaid_throwsException() {
                order.markFailed("fail");

                assertThatThrownBy(() -> order.markPaid("response"))
                        .isInstanceOf(DomainException.class);
            }

            @Test
            @DisplayName("결제 완료 상태에서 불확실로 전이할 수 없다")
            void paidToUncertain_throwsException() {
                order.markPaid("response");

                assertThatThrownBy(() -> order.markUncertain("uncertain"))
                        .isInstanceOf(DomainException.class);
            }

            @Test
            @DisplayName("결제 완료 상태에서 보상 중으로 전이할 수 없다")
            void paidToCompensating_throwsException() {
                order.markPaid("response");

                assertThatThrownBy(() -> order.markCompensating())
                        .isInstanceOf(DomainException.class);
            }

            @Test
            @DisplayName("대기 상태에서 바로 취소로 전이할 수 없다")
            void pendingToCanceled_throwsException() {
                assertThatThrownBy(() -> order.markCanceled())
                        .isInstanceOf(DomainException.class);
            }

            @Test
            @DisplayName("결제 완료 상태에서 취소로 전이할 수 없다")
            void paidToCanceled_throwsException() {
                order.markPaid("response");

                assertThatThrownBy(() -> order.markCanceled())
                        .isInstanceOf(DomainException.class);
            }

            @Test
            @DisplayName("실패 상태에서 다시 실패로 전이할 수 없다")
            void failedToFailed_throwsException() {
                order.markFailed("fail");

                assertThatThrownBy(() -> order.markFailed("fail2"))
                        .isInstanceOf(DomainException.class);
            }
        }
    }

    @Nested
    @DisplayName("결제 라인 관리")
    class PaymentLineManagement {

        @Test
        @DisplayName("대기 상태에서 결제 라인을 추가할 수 있다")
        void canAddPaymentLine() {
            PaymentLine line = PaymentLine.of(
                    PaymentMethodCode.CREDIT_CARD,
                    Money.of(50000)
            );

            order.addPaymentLine(line);

            assertThat(order.getPaymentLines()).hasSize(1);
        }

        @Test
        @DisplayName("null 결제 라인을 추가하면 예외가 발생한다")
        void nullPaymentLine_throwsException() {
            assertThatThrownBy(() -> order.addPaymentLine(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("대기 상태가 아니면 결제 라인을 추가할 수 없다")
        void nonPendingStatus_throwsException() {
            order.markPaid("response");

            assertThatThrownBy(() -> order.addPaymentLine(
                    PaymentLine.of(
                            PaymentMethodCode.CREDIT_CARD,
                            Money.of(50000)
                    ))).isInstanceOf(DomainException.class);
        }

        @Test
        @DisplayName("순서로 결제 라인을 승인할 수 있다")
        void approvesPaymentLineBySequence() {
            order.addPaymentLine(
                    PaymentLine.of(
                            PaymentMethodCode.CREDIT_CARD,
                            Money.of(50000)
                    ));

            order.approvePaymentLine(2, "tx-123");

            assertThat(order.getPaymentLines().get(0).getStatus()).isEqualTo(PaymentLineStatus.APPROVED);
            assertThat(order.getPaymentLines().get(0).getPgTxId()).isEqualTo("tx-123");
        }

        @Test
        @DisplayName("순서로 결제 라인을 거절할 수 있다")
        void declinesPaymentLineBySequence() {
            order.addPaymentLine(
                    PaymentLine.of(
                            PaymentMethodCode.CREDIT_CARD,
                            Money.of(50000)
                    ));

            order.declinePaymentLine(2);

            assertThat(order.getPaymentLines().get(0).getStatus()).isEqualTo(PaymentLineStatus.DECLINED);
        }

        @Test
        @DisplayName("순서로 결제 라인을 취소할 수 있다")
        void cancelsPaymentLineBySequence() {
            order.addPaymentLine(
                    PaymentLine.of(
                            PaymentMethodCode.CREDIT_CARD,
                            Money.of(50000)
                    ));
            order.approvePaymentLine(2, "tx-123");

            order.cancelPaymentLine(2);

            assertThat(order.getPaymentLines().get(0).getStatus()).isEqualTo(PaymentLineStatus.CANCELED);
        }

        @Test
        @DisplayName("순서로 결제 라인 ID를 할당할 수 있다")
        void assignsPaymentLineIdBySequence() {
            order.addPaymentLine(
                    PaymentLine.of(
                            PaymentMethodCode.CREDIT_CARD,
                            Money.of(50000)
                    ));

            order.assignPaymentLineId(2, 99L);

            assertThat(order.getPaymentLines().get(0).getId()).isEqualTo(99L);
        }

        @Test
        @DisplayName("존재하지 않는 순서로 결제 라인을 조회하면 예외가 발생한다")
        void unknownSequence_throwsException() {
            assertThatThrownBy(() -> order.approvePaymentLine(9, "tx-123"))
                    .isInstanceOf(DomainException.class).hasMessageContaining("seq=9");
        }

        @Test
        @DisplayName("결제 라인 목록은 외부에서 수정할 수 없다")
        void paymentLines_areUnmodifiable() {
            order.addPaymentLine(
                    PaymentLine.of(
                            PaymentMethodCode.CREDIT_CARD,
                            Money.of(50000)
                    ));

            List<PaymentLine> lines = order.getPaymentLines();

            assertThatThrownBy(() -> lines.add(
                    PaymentLine.of(
                            PaymentMethodCode.YPAY,
                            Money.of(1000)
                    ))).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("ID 할당")
    class AssignId {

        @Test
        @DisplayName("ID를 할당할 수 있다")
        void assignsId() {
            order.assignId(100L);

            assertThat(order.getId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("ID가 이미 할당된 경우 예외가 발생한다")
        void alreadyAssigned_throwsException() {
            order.assignId(100L);

            assertThatThrownBy(() -> order.assignId(200L))
                    .isInstanceOf(DomainException.class).hasMessageContaining("already assigned");
        }
    }
}