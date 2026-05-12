package com.flashsale.domain.point;

import com.flashsale.common.exception.DomainException;
import com.flashsale.domain.shared.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PointAccount")
class PointAccountTest {

    @Nested
    @DisplayName("생성")
    class Create {

        @Test
        @DisplayName("신규 계좌를 생성하면 버전이 0이다")
        void newAccount_versionIsZero() {
            PointAccount account = PointAccount.of(
                    1L,
                    10000L
            );

            assertThat(account.getUserId()).isEqualTo(1L);
            assertThat(account.getBalance()).isEqualTo(10000L);
            assertThat(account.getVersion()).isZero();
        }

        @Test
        @DisplayName("기존 계좌를 복원하면 저장된 버전이 유지된다")
        void existingAccount_preservesVersion() {
            PointAccount account = PointAccount.restore(
                    1L,
                    5000L,
                    3L
            );

            assertThat(account.getUserId()).isEqualTo(1L);
            assertThat(account.getBalance()).isEqualTo(5000L);
            assertThat(account.getVersion()).isEqualTo(3L);
        }

        @Test
        @DisplayName("양수가 아닌 사용자 ID로 생성하면 예외가 발생한다")
        void nonPositiveUserId_throwsException() {
            assertThatThrownBy(() -> PointAccount.of(
                    0L,
                    1000L
            )).isInstanceOf(DomainException.class).hasMessageContaining("userId");
        }

        @Test
        @DisplayName("잔액이 음수이면 예외가 발생한다")
        void negativeBalance_throwsException() {
            assertThatThrownBy(() -> PointAccount.of(
                    1L,
                    -1L
            )).isInstanceOf(DomainException.class).hasMessageContaining("non-negative");
        }

        @Test
        @DisplayName("음수 잔액으로 복원하면 예외가 발생한다")
        void restoreWithNegativeBalance_throwsException() {
            assertThatThrownBy(() -> PointAccount.restore(
                    1L,
                    -1L,
                    0L
            )).isInstanceOf(DomainException.class).hasMessageContaining("non-negative");
        }
    }

    @Nested
    @DisplayName("포인트 차감")
    class Deduct {

        @Test
        @DisplayName("잔액 이하 금액을 차감하면 잔액이 줄어든다")
        void withinBalance_reducesBalance() {
            PointAccount account = PointAccount.of(
                    1L,
                    10000L
            );

            account.deduct(Money.of(3000));

            assertThat(account.getBalance()).isEqualTo(7000L);
        }

        @Test
        @DisplayName("잔액과 동일한 금액을 차감하면 잔액이 0이 된다")
        void exactBalance_balanceBecomesZero() {
            PointAccount account = PointAccount.of(
                    1L,
                    1000L
            );

            account.deduct(Money.of(1000));

            assertThat(account.getBalance()).isZero();
        }

        @Test
        @DisplayName("잔액을 초과하는 금액을 차감하면 예외가 발생한다")
        void exceedsBalance_throwsException() {
            PointAccount account = PointAccount.of(
                    1L,
                    1000L
            );

            assertThatThrownBy(() -> account.deduct(Money.of(2000)))
                    .isInstanceOf(DomainException.class).hasMessageContaining("Insufficient");
        }
    }

    @Nested
    @DisplayName("포인트 환불")
    class Refund {

        @Test
        @DisplayName("환불하면 잔액이 증가한다")
        void increasesBalance() {
            PointAccount account = PointAccount.of(
                    1L,
                    5000L
            );

            account.refund(Money.of(2000));

            assertThat(account.getBalance()).isEqualTo(7000L);
        }
    }
}