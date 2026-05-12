package com.flashsale.domain.shared;

import com.flashsale.common.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Money")
class MoneyTest {

    @Nested
    @DisplayName("생성")
    class Create {

        @Test
        @DisplayName("양수 금액으로 생성할 수 있다")
        void positiveAmount() {
            Money money = Money.of(1000);

            assertThat(money.amount()).isEqualTo(1000);
        }

        @Test
        @DisplayName("0원으로 생성할 수 있다")
        void zeroAmount() {
            Money money = Money.of(0);

            assertThat(money.amount()).isZero();
        }

        @Test
        @DisplayName("음수 금액으로 생성하면 예외가 발생한다")
        void negativeAmount_throwsException() {
            assertThatThrownBy(() -> Money.of(-1))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("negative");
        }

        @Test
        @DisplayName("0원을 표현하는 상수를 제공한다")
        void zeroConstant() {
            assertThat(Money.ZERO.amount()).isZero();
        }
    }

    @Nested
    @DisplayName("덧셈")
    class Plus {

        @Test
        @DisplayName("두 금액을 더하면 합산된 금액을 반환한다")
        void returnsSum() {
            Money result = Money.of(1000).plus(Money.of(500));

            assertThat(result.amount()).isEqualTo(1500);
        }

        @Test
        @DisplayName("0원에 더하면 원본 금액을 그대로 반환한다")
        void withZero_returnsOriginal() {
            Money result = Money.ZERO.plus(Money.of(500));

            assertThat(result.amount()).isEqualTo(500);
        }
    }

    @Nested
    @DisplayName("뺄셈")
    class Minus {

        @Test
        @DisplayName("큰 금액에서 작은 금액을 빼면 차액을 반환한다")
        void returnsDifference() {
            Money result = Money.of(1000).minus(Money.of(300));

            assertThat(result.amount()).isEqualTo(700);
        }

        @Test
        @DisplayName("같은 금액을 빼면 0원을 반환한다")
        void sameAmount_returnsZero() {
            Money result = Money.of(500).minus(Money.of(500));

            assertThat(result.amount()).isZero();
        }

        @Test
        @DisplayName("잔액보다 큰 금액을 빼면 예외가 발생한다")
        void insufficientBalance_throwsException() {
            assertThatThrownBy(() -> Money.of(100).minus(Money.of(200)))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("Insufficient");
        }
    }

    @Nested
    @DisplayName("비교")
    class IsLessThan {

        @Test
        @DisplayName("더 큰 금액과 비교하면 작다고 판별한다")
        void smallerThanOther_returnsTrue() {
            assertThat(Money.of(100).isLessThan(Money.of(200))).isTrue();
        }

        @Test
        @DisplayName("더 작은 금액과 비교하면 작지 않다고 판별한다")
        void greaterThanOther_returnsFalse() {
            assertThat(Money.of(200).isLessThan(Money.of(100))).isFalse();
        }

        @Test
        @DisplayName("같은 금액과 비교하면 작지 않다고 판별한다")
        void equalToOther_returnsFalse() {
            assertThat(Money.of(100).isLessThan(Money.of(100))).isFalse();
        }
    }
}