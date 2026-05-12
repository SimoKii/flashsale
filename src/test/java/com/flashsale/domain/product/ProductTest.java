package com.flashsale.domain.product;

import com.flashsale.common.exception.DomainException;
import com.flashsale.domain.shared.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Product")
class ProductTest {

    private static final LocalDateTime CHECKIN = LocalDateTime.of(2026, 6, 1, 15, 0);
    private static final LocalDateTime CHECKOUT = LocalDateTime.of(2026, 6, 3, 11, 0);
    private static final LocalDateTime SALE_OPEN = LocalDateTime.of(2026, 5, 1, 0, 0);

    @Nested
    @DisplayName("생성")
    class Create {

        @Test
        @DisplayName("유효한 값으로 생성할 수 있다")
        void validValues() {
            Product product = Product.restore(1L, "제주 특가 숙소", Money.of(50000), CHECKIN, CHECKOUT, SALE_OPEN);

            assertThat(product.getId()).isEqualTo(1L);
            assertThat(product.getName()).isEqualTo("제주 특가 숙소");
            assertThat(product.getPrice().amount()).isEqualTo(50000);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "\t"})
        @DisplayName("상품명이 공백이면 예외가 발생한다")
        void blankName_throwsException(final String name) {
            assertThatThrownBy(() -> Product.restore(1L, name, Money.of(50000), CHECKIN, CHECKOUT, SALE_OPEN))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("blank");
        }

        @Test
        @DisplayName("가격이 null이면 예외가 발생한다")
        void nullPrice_throwsException() {
            assertThatThrownBy(() -> Product.restore(1L, "숙소", null, CHECKIN, CHECKOUT, SALE_OPEN))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("체크인 일시가 null이면 예외가 발생한다")
        void nullCheckinAt_throwsException() {
            assertThatThrownBy(() -> Product.restore(1L, "숙소", Money.of(1000), null, CHECKOUT, SALE_OPEN))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("체크아웃 일시가 null이면 예외가 발생한다")
        void nullCheckoutAt_throwsException() {
            assertThatThrownBy(() -> Product.restore(1L, "숙소", Money.of(1000), CHECKIN, null, SALE_OPEN))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("판매 오픈 일시가 null이면 예외가 발생한다")
        void nullSaleOpenAt_throwsException() {
            assertThatThrownBy(() -> Product.restore(1L, "숙소", Money.of(1000), CHECKIN, CHECKOUT, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("체크아웃이 체크인 이전이면 예외가 발생한다")
        void checkoutBeforeCheckin_throwsException() {
            assertThatThrownBy(() -> Product.restore(1L, "숙소", Money.of(1000), CHECKOUT, CHECKIN, SALE_OPEN))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("checkoutAt must be after checkinAt");
        }

        @Test
        @DisplayName("체크아웃과 체크인이 같으면 예외가 발생한다")
        void checkoutEqualsCheckin_throwsException() {
            assertThatThrownBy(() -> Product.restore(1L, "숙소", Money.of(1000), CHECKIN, CHECKIN, SALE_OPEN))
                    .isInstanceOf(DomainException.class);
        }
    }

    @Nested
    @DisplayName("판매 여부 확인")
    class IsOnSale {

        @Test
        @DisplayName("판매 오픈 시각 이후이면 판매 중이다")
        void afterSaleOpen_onSale() {
            Product product = Product.restore(1L, "숙소", Money.of(50000), CHECKIN, CHECKOUT, SALE_OPEN);

            assertThat(product.isOnSale(SALE_OPEN)).isTrue();
            assertThat(product.isOnSale(SALE_OPEN.plusSeconds(1))).isTrue();
        }

        @Test
        @DisplayName("판매 오픈 시각 이전이면 판매 중이 아니다")
        void beforeSaleOpen_notOnSale() {
            Product product = Product.restore(1L, "숙소", Money.of(50000), CHECKIN, CHECKOUT, SALE_OPEN);

            assertThat(product.isOnSale(SALE_OPEN.minusSeconds(1))).isFalse();
        }
    }
}