package com.flashsale.domain.order;

import com.flashsale.common.exception.DomainException;
import com.flashsale.domain.shared.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PaymentComposition")
class PaymentCompositionTest {

    @Nested
    @DisplayName("입력 유효성")
    class InputValidation {

        @Test
        @DisplayName("결제 라인이 없으면 예외가 발생한다")
        void emptyLines_throwsException() {
            assertThatThrownBy(() -> PaymentComposition.validate(
                    List.of(),
                    Money.of(50000)
            )).isInstanceOf(DomainException.class).hasMessageContaining("empty");
        }

        @Test
        @DisplayName("결제 라인 목록이 null이면 예외가 발생한다")
        void nullLines_throwsException() {
            assertThatThrownBy(() -> PaymentComposition.validate(
                    null,
                    Money.of(50000)
            )).isInstanceOf(DomainException.class);
        }
    }

    @Nested
    @DisplayName("결제 수단 조합 규칙")
    class CombinationRule {

        @Test
        @DisplayName("신용카드와 YPAY를 함께 사용하면 예외가 발생한다")
        void creditCardAndYPay_throwsException() {
            List<PaymentLine> lines = List.of(
                    PaymentLine.of(
                            PaymentMethodCode.CREDIT_CARD,
                            Money.of(30000)
                    ),
                    PaymentLine.of(
                            PaymentMethodCode.YPAY,
                            Money.of(20000)
                    )
            );

            assertThatThrownBy(() -> PaymentComposition.validate(
                    lines,
                    Money.of(50000)
            )).isInstanceOf(DomainException.class).hasMessageContaining("CREDIT_CARD and YPAY");
        }

        @Test
        @DisplayName("포인트 라인이 2개이면 예외가 발생한다")
        void duplicatePointLines_throwsException() {
            List<PaymentLine> lines = List.of(
                    PaymentLine.of(
                            PaymentMethodCode.YPOINT,
                            Money.of(10000)
                    ),
                    PaymentLine.of(
                            PaymentMethodCode.YPOINT,
                            Money.of(10000)
                    )
            );

            assertThatThrownBy(() -> PaymentComposition.validate(
                    lines,
                    Money.of(20000)
            )).isInstanceOf(DomainException.class).hasMessageContaining("YPOINT");
        }

        @Test
        @DisplayName("외부 결제 라인이 2개이면 예외가 발생한다")
        void duplicateExternalLines_throwsException() {
            List<PaymentLine> lines = List.of(
                    PaymentLine.of(
                            PaymentMethodCode.CREDIT_CARD,
                            Money.of(25000)
                    ),
                    PaymentLine.of(
                            PaymentMethodCode.CREDIT_CARD,
                            Money.of(25000)
                    )
            );

            assertThatThrownBy(() -> PaymentComposition.validate(
                    lines,
                    Money.of(50000)
            )).isInstanceOf(DomainException.class).hasMessageContaining("external");
        }
    }

    @Nested
    @DisplayName("금액 일치")
    class AmountMatching {

        @Test
        @DisplayName("결제 라인 합계가 총액보다 적으면 예외가 발생한다")
        void sumLessThanTotal_throwsException() {
            List<PaymentLine> lines = List.of(
                    PaymentLine.of(
                            PaymentMethodCode.CREDIT_CARD,
                            Money.of(30000)
                    )
            );

            assertThatThrownBy(() -> PaymentComposition.validate(
                    lines,
                    Money.of(50000)
            )).isInstanceOf(DomainException.class).hasMessageContaining("!=");
        }

        @Test
        @DisplayName("결제 라인 합계가 총액보다 크면 예외가 발생한다")
        void sumGreaterThanTotal_throwsException() {
            List<PaymentLine> lines = List.of(
                    PaymentLine.of(
                            PaymentMethodCode.CREDIT_CARD,
                            Money.of(60000)
                    )
            );

            assertThatThrownBy(() -> PaymentComposition.validate(
                    lines,
                    Money.of(50000)
            )).isInstanceOf(DomainException.class).hasMessageContaining("!=");
        }

        @Test
        @DisplayName("포인트와 외부 결제 합계가 총액과 다르면 예외가 발생한다")
        void compositeSumMismatch_throwsException() {
            List<PaymentLine> lines = List.of(
                    PaymentLine.of(
                            PaymentMethodCode.YPOINT,
                            Money.of(10000)
                    ),
                    PaymentLine.of(
                            PaymentMethodCode.CREDIT_CARD,
                            Money.of(30000)
                    )
            );

            assertThatThrownBy(() -> PaymentComposition.validate(
                    lines,
                    Money.of(50000)
            )).isInstanceOf(DomainException.class).hasMessageContaining("!=");
        }
    }

    @Nested
    @DisplayName("허용되는 조합")
    class ValidCombinations {

        @Test
        @DisplayName("포인트 단독 결제는 허용된다")
        void pointOnly_isAllowed() {
            List<PaymentLine> lines = List.of(
                    PaymentLine.of(
                            PaymentMethodCode.YPOINT,
                            Money.of(50000)
                    )
            );

            assertThatNoException().isThrownBy(() -> PaymentComposition.validate(
                    lines,
                    Money.of(50000)
            ));
        }

        @Test
        @DisplayName("신용카드 단독 결제는 허용된다")
        void creditCardOnly_isAllowed() {
            List<PaymentLine> lines = List.of(
                    PaymentLine.of(
                            PaymentMethodCode.CREDIT_CARD,
                            Money.of(50000)
                    )
            );

            assertThatNoException().isThrownBy(() -> PaymentComposition.validate(
                    lines,
                    Money.of(50000)
            ));
        }

        @Test
        @DisplayName("YPAY 단독 결제는 허용된다")
        void yPayOnly_isAllowed() {
            List<PaymentLine> lines = List.of(
                    PaymentLine.of(
                            PaymentMethodCode.YPAY,
                            Money.of(50000)
                    )
            );

            assertThatNoException().isThrownBy(() -> PaymentComposition.validate(
                    lines,
                    Money.of(50000)
            ));
        }

        @Test
        @DisplayName("포인트와 신용카드 복합 결제는 허용된다")
        void pointAndCreditCard_isAllowed() {
            List<PaymentLine> lines = List.of(
                    PaymentLine.of(
                            PaymentMethodCode.YPOINT,
                            Money.of(10000)
                    ),
                    PaymentLine.of(
                            PaymentMethodCode.CREDIT_CARD,
                            Money.of(40000)
                    )
            );

            assertThatNoException().isThrownBy(() -> PaymentComposition.validate(
                    lines,
                    Money.of(50000)
            ));
        }

        @Test
        @DisplayName("포인트와 YPAY 복합 결제는 허용된다")
        void pointAndYPay_isAllowed() {
            List<PaymentLine> lines = List.of(
                    PaymentLine.of(
                            PaymentMethodCode.YPOINT,
                            Money.of(10000)
                    ),
                    PaymentLine.of(
                            PaymentMethodCode.YPAY,
                            Money.of(40000)
                    )
            );

            assertThatNoException().isThrownBy(() -> PaymentComposition.validate(
                    lines,
                    Money.of(50000)
            ));
        }
    }
}