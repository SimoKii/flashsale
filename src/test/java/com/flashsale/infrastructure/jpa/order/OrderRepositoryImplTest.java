package com.flashsale.infrastructure.jpa.order;

import com.flashsale.application.booking.port.OrderRepository;
import com.flashsale.domain.order.IdempotencyKey;
import com.flashsale.domain.order.Order;
import com.flashsale.domain.order.OrderStatus;
import com.flashsale.domain.order.PaymentLine;
import com.flashsale.domain.order.PaymentLineStatus;
import com.flashsale.domain.order.PaymentMethodCode;
import com.flashsale.domain.shared.Money;
import com.flashsale.infrastructure.jpa.order.impl.OrderRepositoryImpl;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(OrderRepositoryImpl.class)
@DisplayName("OrderRepository")
class OrderRepositoryImplTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("flashsale")
            .withUsername("flashsale")
            .withPassword("flashsalepw");

    @DynamicPropertySource
    static void properties(
            final DynamicPropertyRegistry registry
    ) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    OrderRepository repository;

    @Nested
    @DisplayName("저장")
    class Save {

        @Test
        @DisplayName("신규 주문을 저장하면 ID가 부여된다")
        void newOrder_assignsId() {
            Order order = repository.save(newOrder("key-00001"));

            assertThat(order.getId()).isNotNull();
        }

        @Test
        @DisplayName("주문을 저장하면 결제 라인도 함께 저장된다")
        void orderWithPaymentLines_persistsLinesCascade() {
            Order order = newOrder("key-00002");
            order.addPaymentLine(
                    PaymentLine.of(
                            PaymentMethodCode.YPOINT,
                            Money.of(10_000)
                    ));
            order.addPaymentLine(
                    PaymentLine.of(
                            PaymentMethodCode.CREDIT_CARD,
                            Money.of(40_000)
                    ));

            Order saved = repository.save(order);
            Order found = repository.findById(saved.getId()).orElseThrow();

            assertThat(found.getPaymentLines()).hasSize(2);
        }

        @Test
        @DisplayName("결제 라인의 결제 수단과 금액이 함께 저장된다")
        void paymentLineFields_arePersisted() {
            Order order = newOrder("key-00003");
            order.addPaymentLine(
                    PaymentLine.of(
                            PaymentMethodCode.YPOINT,
                            Money.of(10_000)
                    ));
            order.addPaymentLine(
                    PaymentLine.of(
                            PaymentMethodCode.CREDIT_CARD,
                            Money.of(40_000)
                    ));

            Order saved = repository.save(order);
            Order found = repository.findById(saved.getId()).orElseThrow();

            assertThat(found.getPaymentLines())
                    .extracting(PaymentLine::getMethod, line -> line.getAmount().amount())
                    .containsExactlyInAnyOrder(
                            tuple(PaymentMethodCode.YPOINT, 10_000L),
                            tuple(PaymentMethodCode.CREDIT_CARD, 40_000L)
                    );
        }

        @Test
        @DisplayName("동일한 멱등성 키로 중복 저장하면 예외가 발생한다")
        void duplicateIdempotencyKey_throwsException() {
            repository.save(newOrder("key-dupli"));

            assertThatThrownBy(() -> repository.save(newOrder("key-dupli")))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("ID 조회")
    class FindById {

        @Test
        @DisplayName("저장된 주문을 ID로 조회하면 도메인 객체가 복원된다")
        void existingOrder_isRestoredAsDomainObject() {
            Order order = newOrder("key-00004");
            order.addPaymentLine(
                    PaymentLine.of(
                            PaymentMethodCode.CREDIT_CARD,
                            Money.of(50_000)
                    ));

            Order saved = repository.save(order);
            Order found = repository.findById(saved.getId()).orElseThrow();

            assertThat(found.getIdempotencyKey().value()).isEqualTo("key-00004");
            assertThat(found.getUserId()).isEqualTo(1L);
            assertThat(found.getProductId()).isEqualTo(1L);
            assertThat(found.getTotalAmount()).isEqualTo(Money.of(50_000));
            assertThat(found.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(found.getPaymentLines()).hasSize(1);
            assertThat(found.getPaymentLines().get(0).getMethod()).isEqualTo(PaymentMethodCode.CREDIT_CARD);
        }

        @Test
        @DisplayName("존재하지 않는 ID로 조회하면 빈 값을 반환한다")
        void nonExistingId_returnsEmpty() {
            assertThat(repository.findById(999_999L)).isEmpty();
        }
    }

    @Nested
    @DisplayName("멱등성 키 조회")
    class FindByIdempotencyKey {

        @Test
        @DisplayName("저장된 주문을 멱등성 키로 조회할 수 있다")
        void existingKey_returnsOrder() {
            repository.save(newOrder("key-00005"));

            Order found = repository.findByIdempotencyKey("key-00005").orElseThrow();

            assertThat(found.getIdempotencyKey().value()).isEqualTo("key-00005");
        }

        @Test
        @DisplayName("존재하지 않는 멱등성 키로 조회하면 빈 값을 반환한다")
        void nonExistingKey_returnsEmpty() {
            assertThat(repository.findByIdempotencyKey("nonexistent")).isEmpty();
        }
    }

    @Nested
    @DisplayName("상태 갱신")
    class StatusUpdate {

        @Test
        @DisplayName("저장된 주문의 상태를 변경하면 갱신된다")
        void markPaid_isPersisted() {
            Order order = repository.save(newOrder("key-00006"));
            order.markPaid("pg-response");
            repository.save(order);

            Order found = repository.findById(order.getId()).orElseThrow();

            assertThat(found.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(found.getResponseBody()).isEqualTo("pg-response");
        }

        @Test
        @DisplayName("결제 라인의 상태 변경도 함께 저장된다")
        void paymentLineStatusUpdate_isPersisted() {
            Order order = newOrder("key-00007");
            order.addPaymentLine(
                    PaymentLine.of(
                            PaymentMethodCode.CREDIT_CARD,
                            Money.of(50_000)
                    ));

            Order saved = repository.save(order);

            saved.approvePaymentLine(2, "pg-tx-12345");
            repository.save(saved);

            Order found = repository.findById(saved.getId()).orElseThrow();
            assertThat(found.getPaymentLines().get(0).getStatus()).isEqualTo(PaymentLineStatus.APPROVED);
            assertThat(found.getPaymentLines().get(0).getPgTxId()).isEqualTo("pg-tx-12345");
        }
    }

    private Order newOrder(
            final String idempotencyKey
    ) {
        return Order.create(
                IdempotencyKey.of(idempotencyKey),
                1L,
                1L,
                Money.of(50_000),
                LocalDateTime.now().plusMinutes(5)
        );
    }

    private static Tuple tuple(
            final Object... values
    ) {
        return Tuple.tuple(values);
    }
}