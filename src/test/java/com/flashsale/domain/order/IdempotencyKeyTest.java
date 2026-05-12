package com.flashsale.domain.order;

import com.flashsale.common.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("IdempotencyKey")
class IdempotencyKeyTest {

    @Nested
    @DisplayName("생성")
    class Create {

        @Test
        @DisplayName("유효한 값으로 생성할 수 있다")
        void validValue() {
            IdempotencyKey key = IdempotencyKey.of("order-abc-12345678");

            assertThat(key.value()).isEqualTo("order-abc-12345678");
        }

        @ParameterizedTest(name = "[{index}] value=\"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {" ", "\t"})
        @DisplayName("null 또는 공백이면 예외가 발생한다")
        void nullOrBlank_throwsException(final String value) {
            assertThatThrownBy(() -> IdempotencyKey.of(value))
                    .isInstanceOf(DomainException.class);
        }

        @Test
        @DisplayName("최소 길이 미만이면 예외가 발생한다")
        void tooShort_throwsException() {
            assertThatThrownBy(() -> IdempotencyKey.of("short"))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("Invalid idempotency key");
        }

        @Test
        @DisplayName("최대 길이를 초과하면 예외가 발생한다")
        void tooLong_throwsException() {
            assertThatThrownBy(() -> IdempotencyKey.of("a".repeat(65)))
                    .isInstanceOf(DomainException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"key!@#$1234", "key 1234567", "한글키12345678"})
        @DisplayName("허용되지 않는 문자가 포함되면 예외가 발생한다")
        void invalidCharacters_throwsException(final String value) {
            assertThatThrownBy(() -> IdempotencyKey.of(value))
                    .isInstanceOf(DomainException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"abcdefgh", "order-12345678", "ORDER-KEY-12345678901234"})
        @DisplayName("허용된 문자 패턴으로 생성할 수 있다")
        void validPattern(final String value) {
            assertThat(IdempotencyKey.of(value).value()).isEqualTo(value);
        }

        @Test
        @DisplayName("최소 길이 경계값으로 생성할 수 있다")
        void minLengthBoundary() {
            assertThat(IdempotencyKey.of("12345678").value()).hasSize(8);
        }

        @Test
        @DisplayName("최대 길이 경계값으로 생성할 수 있다")
        void maxLengthBoundary() {
            assertThat(IdempotencyKey.of("a".repeat(64)).value()).hasSize(64);
        }
    }
}