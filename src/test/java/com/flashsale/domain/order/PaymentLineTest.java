package com.flashsale.domain.order;

import com.flashsale.common.exception.DomainException;
import com.flashsale.domain.shared.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PaymentLine")
class PaymentLineTest {

    @Nested
    @DisplayName("생성")
    class Create {

        @Test
        @DisplayName("포인트 결제수단으로 생성하면 순서가 1이고 요청 상태이다")
        void pointMethod_sequenceIsOne() {
            PaymentLine line = PaymentLine.of(
                    PaymentMethodCode.YPOINT,
                    Money.of(10000)
            );

            assertThat(line.getId()).isNull();
            assertThat(line.getMethod()).isEqualTo(PaymentMethodCode.YPOINT);
            assertThat(line.getAmount().amount()).isEqualTo(10000);
            assertThat(line.getStatus()).isEqualTo(PaymentLineStatus.REQUESTED);
            assertThat(line.getSequence()).isEqualTo(1);
            assertThat(line.getPgTxId()).isNull();
        }

        @Test
        @DisplayName("외부 결제수단으로 생성하면 순서가 2이다")
        void externalMethod_sequenceIsTwo() {
            PaymentLine line = PaymentLine.of(
                    PaymentMethodCode.CREDIT_CARD,
                    Money.of(40000)
            );

            assertThat(line.getSequence()).isEqualTo(2);
        }

        @Test
        @DisplayName("기존 결제 라인을 복원하면 저장된 상태와 PG 거래 ID가 유지된다")
        void existingLine_preservesStatusAndPgTxId() {
            PaymentLine line = PaymentLine.restore(
                    1L,
                    PaymentMethodCode.YPAY,
                    Money.of(50000),
                    PaymentLineStatus.APPROVED,
                    "tx-001",
                    2
            );

            assertThat(line.getId()).isEqualTo(1L);
            assertThat(line.getMethod()).isEqualTo(PaymentMethodCode.YPAY);
            assertThat(line.getAmount().amount()).isEqualTo(50000);
            assertThat(line.getStatus()).isEqualTo(PaymentLineStatus.APPROVED);
            assertThat(line.getPgTxId()).isEqualTo("tx-001");
            assertThat(line.getSequence()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("승인")
    class Approve {

        @Test
        @DisplayName("요청 상태에서 승인하면 승인 상태가 된다")
        void fromRequested_becomesApproved() {
            PaymentLine line = PaymentLine.of(
                    PaymentMethodCode.CREDIT_CARD,
                    Money.of(50000)
            );

            line.markApproved("tx-123");

            assertThat(line.getStatus()).isEqualTo(PaymentLineStatus.APPROVED);
            assertThat(line.getPgTxId()).isEqualTo("tx-123");
        }

        @Test
        @DisplayName("PG 거래 ID가 null이면 예외가 발생한다")
        void nullPgTxId_throwsException() {
            PaymentLine line = PaymentLine.of(
                    PaymentMethodCode.CREDIT_CARD,
                    Money.of(50000)
            );

            assertThatThrownBy(() -> line.markApproved(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("PG 거래 ID가 공백이면 예외가 발생한다")
        void blankPgTxId_throwsException() {
            PaymentLine line = PaymentLine.of(
                    PaymentMethodCode.CREDIT_CARD,
                    Money.of(50000)
            );

            assertThatThrownBy(() -> line.markApproved("  "))
                    .isInstanceOf(DomainException.class).hasMessageContaining("pgTxId");
        }

        @Test
        @DisplayName("거절 상태에서 승인하면 예외가 발생한다")
        void fromDeclined_throwsException() {
            PaymentLine line = PaymentLine.of(
                    PaymentMethodCode.CREDIT_CARD,
                    Money.of(50000)
            );

            line.markDeclined();

            assertThatThrownBy(() -> line.markApproved("tx-456"))
                    .isInstanceOf(DomainException.class);
        }

        @Test
        @DisplayName("취소 상태에서 승인하면 예외가 발생한다")
        void fromCanceled_throwsException() {
            PaymentLine line = PaymentLine.of(
                    PaymentMethodCode.CREDIT_CARD,
                    Money.of(50000)
            );

            line.markApproved("tx-123");
            line.markCanceled();

            assertThatThrownBy(() -> line.markApproved("tx-456"))
                    .isInstanceOf(DomainException.class);
        }

        @Test
        @DisplayName("취소 대기 상태에서 승인하면 예외가 발생한다")
        void fromCancelPending_throwsException() {
            PaymentLine line = PaymentLine.of(
                    PaymentMethodCode.CREDIT_CARD,
                    Money.of(50000)
            );

            line.markApproved("tx-123");
            line.markCancelPending();

            assertThatThrownBy(() -> line.markApproved("tx-456"))
                    .isInstanceOf(DomainException.class);
        }
    }

    @Nested
    @DisplayName("거절")
    class Decline {

        @Test
        @DisplayName("요청 상태에서 거절하면 거절 상태가 된다")
        void fromRequested_becomesDeclined() {
            PaymentLine line = PaymentLine.of(
                    PaymentMethodCode.CREDIT_CARD,
                    Money.of(50000)
            );

            line.markDeclined();

            assertThat(line.getStatus()).isEqualTo(PaymentLineStatus.DECLINED);
        }

        @Test
        @DisplayName("이미 거절된 상태에서 거절하면 예외가 발생한다")
        void alreadyDeclined_throwsException() {
            PaymentLine line = PaymentLine.of(
                    PaymentMethodCode.CREDIT_CARD,
                    Money.of(50000)
            );

            line.markDeclined();

            assertThatThrownBy(line::markDeclined)
                    .isInstanceOf(DomainException.class);
        }

        @Test
        @DisplayName("승인 상태에서 거절하면 예외가 발생한다")
        void fromApproved_throwsException() {
            PaymentLine line = PaymentLine.of(
                    PaymentMethodCode.CREDIT_CARD,
                    Money.of(50000)
            );

            line.markApproved("tx-123");

            assertThatThrownBy(line::markDeclined)
                    .isInstanceOf(DomainException.class);
        }
    }

    @Nested
    @DisplayName("취소")
    class Cancel {

        @Test
        @DisplayName("승인 상태에서 바로 취소할 수 있다")
        void fromApproved_becomesCanceled() {
            PaymentLine line = PaymentLine.of(
                    PaymentMethodCode.CREDIT_CARD,
                    Money.of(50000)
            );

            line.markApproved("tx-123");

            line.markCanceled();

            assertThat(line.getStatus()).isEqualTo(PaymentLineStatus.CANCELED);
        }

        @Test
        @DisplayName("취소 대기를 거쳐 취소할 수 있다")
        void viaCancelPending_becomesCanceled() {
            PaymentLine line = PaymentLine.of(
                    PaymentMethodCode.CREDIT_CARD,
                    Money.of(50000)
            );

            line.markApproved("tx-123");
            line.markCancelPending();

            line.markCanceled();

            assertThat(line.getStatus()).isEqualTo(PaymentLineStatus.CANCELED);
        }

        @Test
        @DisplayName("요청 상태에서 취소하면 예외가 발생한다")
        void fromRequested_throwsException() {
            PaymentLine line = PaymentLine.of(
                    PaymentMethodCode.CREDIT_CARD,
                    Money.of(50000)
            );

            assertThatThrownBy(line::markCanceled)
                    .isInstanceOf(DomainException.class).hasMessageContaining("Cannot cancel");
        }

        @Test
        @DisplayName("거절 상태에서 취소하면 예외가 발생한다")
        void fromDeclined_throwsException() {
            PaymentLine line = PaymentLine.of(
                    PaymentMethodCode.CREDIT_CARD,
                    Money.of(50000)
            );

            line.markDeclined();

            assertThatThrownBy(line::markCanceled)
                    .isInstanceOf(DomainException.class);
        }

        @Test
        @DisplayName("취소 대기 중 취소에 실패하면 취소 실패 상태가 된다")
        void cancelFailed_becomesCancelFailed() {
            PaymentLine line = PaymentLine.of(
                    PaymentMethodCode.CREDIT_CARD,
                    Money.of(50000)
            );

            line.markApproved("tx-123");
            line.markCancelPending();

            line.markCancelFailed();

            assertThat(line.getStatus()).isEqualTo(PaymentLineStatus.CANCEL_FAILED);
        }

        @Test
        @DisplayName("승인되지 않은 상태에서 취소 대기로 전이하면 예외가 발생한다")
        void cancelPendingFromRequested_throwsException() {
            PaymentLine line = PaymentLine.of(
                    PaymentMethodCode.CREDIT_CARD,
                    Money.of(50000)
            );

            assertThatThrownBy(line::markCancelPending)
                    .isInstanceOf(DomainException.class);
        }
    }

    @Nested
    @DisplayName("불확실")
    class Uncertain {

        @Test
        @DisplayName("요청 상태에서 불확실 상태로 전이할 수 있다")
        void fromRequested_becomesUncertain() {
            PaymentLine line = PaymentLine.of(
                    PaymentMethodCode.CREDIT_CARD,
                    Money.of(50000)
            );

            line.markUncertain();

            assertThat(line.getStatus()).isEqualTo(PaymentLineStatus.UNCERTAIN);
        }

        @Test
        @DisplayName("승인 상태에서 불확실로 전이하면 예외가 발생한다")
        void fromApproved_throwsException() {
            PaymentLine line = PaymentLine.of(
                    PaymentMethodCode.CREDIT_CARD,
                    Money.of(50000)
            );

            line.markApproved("tx-123");

            assertThatThrownBy(line::markUncertain)
                    .isInstanceOf(DomainException.class);
        }
    }

    @Nested
    @DisplayName("ID 할당")
    class AssignId {

        @Test
        @DisplayName("ID를 할당할 수 있다")
        void assignsId() {
            PaymentLine line = PaymentLine.of(
                    PaymentMethodCode.CREDIT_CARD,
                    Money.of(50000)
            );

            line.assignId(1L);

            assertThat(line.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("ID가 이미 할당된 경우 예외가 발생한다")
        void alreadyAssigned_throwsException() {
            PaymentLine line = PaymentLine.of(
                    PaymentMethodCode.CREDIT_CARD,
                    Money.of(50000)
            );

            line.assignId(1L);

            assertThatThrownBy(() -> line.assignId(2L))
                    .isInstanceOf(DomainException.class).hasMessageContaining("already assigned");
        }
    }
}