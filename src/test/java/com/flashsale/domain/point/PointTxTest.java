package com.flashsale.domain.point;

import com.flashsale.common.exception.DomainException;
import com.flashsale.domain.shared.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PointTx")
class PointTxTest {

    @Nested
    @DisplayName("생성")
    class Create {

        @Test
        @DisplayName("신규 거래 내역을 생성하면 ID가 없다")
        void newTx_hasNoId() {
            PointTx tx = PointTx.of(
                    1L,
                    100L,
                    PointTx.Type.USE,
                    Money.of(5000)
            );

            assertThat(tx.getId()).isNull();
            assertThat(tx.getUserId()).isEqualTo(1L);
            assertThat(tx.getOrderId()).isEqualTo(100L);
            assertThat(tx.getType()).isEqualTo(PointTx.Type.USE);
            assertThat(tx.getAmount().amount()).isEqualTo(5000);
            assertThat(tx.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("기존 거래 내역을 복원하면 저장된 ID와 생성 시각이 유지된다")
        void existingTx_preservesIdAndCreatedAt() {
            LocalDateTime savedTime = LocalDateTime.of(
                    2026,
                    5,
                    1,
                    0,
                    0
            );

            PointTx tx = PointTx.restore(
                    1L,
                    2L,
                    3L,
                    PointTx.Type.REFUND,
                    Money.of(1000),
                    savedTime
            );

            assertThat(tx.getId()).isEqualTo(1L);
            assertThat(tx.getUserId()).isEqualTo(2L);
            assertThat(tx.getOrderId()).isEqualTo(3L);
            assertThat(tx.getType()).isEqualTo(PointTx.Type.REFUND);
            assertThat(tx.getAmount().amount()).isEqualTo(1000);
            assertThat(tx.getCreatedAt()).isEqualTo(savedTime);
        }

        @Test
        @DisplayName("적립 거래는 주문 없이 생성할 수 있다")
        void earnType_canBeCreatedWithoutOrder() {
            PointTx tx = PointTx.of(
                    1L,
                    null,
                    PointTx.Type.EARN,
                    Money.of(1000)
            );

            assertThat(tx.getOrderId()).isNull();
            assertThat(tx.getType()).isEqualTo(PointTx.Type.EARN);
        }
    }

    @Nested
    @DisplayName("유효성 검증")
    class Validation {

        @Test
        @DisplayName("거래 유형이 null이면 예외가 발생한다")
        void nullType_throwsException() {
            assertThatThrownBy(() -> PointTx.of(
                    1L,
                    null,
                    null,
                    Money.of(1000)
            )).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("금액이 null이면 예외가 발생한다")
        void nullAmount_throwsException() {
            assertThatThrownBy(() -> PointTx.of(
                    1L,
                    null,
                    PointTx.Type.USE,
                    null
            )).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("금액이 0이면 예외가 발생한다")
        void zeroAmount_throwsException() {
            assertThatThrownBy(() -> PointTx.of(
                    1L,
                    null,
                    PointTx.Type.USE,
                    Money.of(0)
            )).isInstanceOf(DomainException.class).hasMessageContaining("positive");
        }

        @Test
        @DisplayName("금액이 음수이면 예외가 발생한다")
        void negativeAmount_throwsException() {
            assertThatThrownBy(() -> PointTx.of(
                    1L,
                    null,
                    PointTx.Type.USE,
                    Money.of(-1)
            )).isInstanceOf(DomainException.class);
        }

        @Test
        @DisplayName("0 금액으로 복원하면 예외가 발생한다")
        void restoreWithZeroAmount_throwsException() {
            LocalDateTime savedTime = LocalDateTime.of(
                    2026,
                    5,
                    1,
                    0,
                    0
            );

            assertThatThrownBy(() -> PointTx.restore(
                    1L,
                    2L,
                    3L,
                    PointTx.Type.USE,
                    Money.of(0),
                    savedTime
            )).isInstanceOf(DomainException.class).hasMessageContaining("positive");
        }
    }
}