package com.flashsale.domain.stock;

import com.flashsale.common.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Stock")
class StockTest {

    @Nested
    @DisplayName("생성")
    class Create {

        @Test
        @DisplayName("신규 재고를 생성하면 예약과 판매 수량이 0이다")
        void newStock_reservedAndSoldAreZero() {
            Stock stock = Stock.of(1L, 10);

            assertThat(stock.getTotal()).isEqualTo(10);
            assertThat(stock.getReserved()).isZero();
            assertThat(stock.getSold()).isZero();
            assertThat(stock.remaining()).isEqualTo(10);
        }

        @Test
        @DisplayName("기존 재고를 복원할 수 있다")
        void existingStock_canBeRestored() {
            Stock stock = Stock.restore(1L, 10, 2, 3, 1L);

            assertThat(stock.getReserved()).isEqualTo(2);
            assertThat(stock.getSold()).isEqualTo(3);
            assertThat(stock.remaining()).isEqualTo(5);
        }

        @Test
        @DisplayName("양수가 아닌 상품 ID로 생성하면 예외가 발생한다")
        void nonPositiveProductId_throwsException() {
            assertThatThrownBy(() -> Stock.of(0L, 10))
                    .isInstanceOf(DomainException.class).hasMessageContaining("productId");
        }

        @Test
        @DisplayName("총량이 음수이면 예외가 발생한다")
        void negativeTotal_throwsException() {
            assertThatThrownBy(() -> Stock.restore(1L, -1, 0, 0, 0))
                    .isInstanceOf(DomainException.class).hasMessageContaining("total");
        }

        @Test
        @DisplayName("예약 수량이 음수이면 예외가 발생한다")
        void negativeReserved_throwsException() {
            assertThatThrownBy(() -> Stock.restore(1L, 10, -1, 0, 0))
                    .isInstanceOf(DomainException.class).hasMessageContaining("reserved");
        }

        @Test
        @DisplayName("판매 수량이 음수이면 예외가 발생한다")
        void negativeSold_throwsException() {
            assertThatThrownBy(() -> Stock.restore(1L, 10, 0, -1, 0))
                    .isInstanceOf(DomainException.class).hasMessageContaining("sold");
        }

        @Test
        @DisplayName("예약과 판매 합계가 총량을 초과하면 예외가 발생한다")
        void reservedPlusSoldExceedsTotal_throwsException() {
            assertThatThrownBy(() -> Stock.restore(1L, 5, 3, 3, 0))
                    .isInstanceOf(DomainException.class).hasMessageContaining("exceeds");
        }
    }

    @Nested
    @DisplayName("재고 예약")
    class Reserve {

        @Test
        @DisplayName("예약하면 예약 수량이 1 증가한다")
        void incrementsReserved() {
            Stock stock = Stock.of(1L, 10);

            stock.reserve();

            assertThat(stock.getReserved()).isEqualTo(1);
            assertThat(stock.remaining()).isEqualTo(9);
        }

        @Test
        @DisplayName("재고가 소진되면 예약 시 예외가 발생한다")
        void exhausted_throwsException() {
            Stock stock = Stock.restore(1L, 2, 1, 1, 0);

            assertThatThrownBy(stock::reserve)
                    .isInstanceOf(DomainException.class).hasMessageContaining("exhausted");
        }
    }

    @Nested
    @DisplayName("예약 취소")
    class RestoreReservation {

        @Test
        @DisplayName("예약을 취소하면 예약 수량이 1 감소한다")
        void decrementsReserved() {
            Stock stock = Stock.restore(1L, 10, 3, 2, 0);

            stock.restore();

            assertThat(stock.getReserved()).isEqualTo(2);
        }

        @Test
        @DisplayName("예약이 없는 상태에서 취소하면 예외가 발생한다")
        void noReservation_throwsException() {
            Stock stock = Stock.of(1L, 10);

            assertThatThrownBy(stock::restore)
                    .isInstanceOf(DomainException.class).hasMessageContaining("Nothing to restore");
        }
    }

    @Nested
    @DisplayName("재고 확정")
    class Confirm {

        @Test
        @DisplayName("확정하면 예약 수량이 줄고 판매 수량이 늘어난다")
        void movesFromReservedToSold() {
            Stock stock = Stock.restore(1L, 10, 2, 3, 0);

            stock.confirm();

            assertThat(stock.getReserved()).isEqualTo(1);
            assertThat(stock.getSold()).isEqualTo(4);
        }

        @Test
        @DisplayName("예약이 없는 상태에서 확정하면 예외가 발생한다")
        void noReservation_throwsException() {
            Stock stock = Stock.of(1L, 10);

            assertThatThrownBy(stock::confirm)
                    .isInstanceOf(DomainException.class).hasMessageContaining("Nothing to confirm");
        }
    }

    @Nested
    @DisplayName("잔여 재고 조회")
    class Remaining {

        @Test
        @DisplayName("잔여 재고는 총량에서 예약과 판매 수량을 뺀 값이다")
        void returnsAvailableStock() {
            Stock stock = Stock.restore(1L, 10, 3, 2, 0);

            assertThat(stock.remaining()).isEqualTo(5);
        }
    }
}