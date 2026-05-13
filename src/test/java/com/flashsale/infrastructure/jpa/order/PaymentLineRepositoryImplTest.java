package com.flashsale.infrastructure.jpa.order;

import com.flashsale.common.exception.DomainException;
import com.flashsale.domain.order.IdempotencyKey;
import com.flashsale.domain.order.Order;
import com.flashsale.domain.order.PaymentLine;
import com.flashsale.domain.order.PaymentLineStatus;
import com.flashsale.domain.order.PaymentMethodCode;
import com.flashsale.domain.shared.Money;
import com.flashsale.infrastructure.jpa.order.impl.PaymentLineRepositoryImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(PaymentLineRepositoryImpl.class)
@DisplayName("PaymentLineRepository")
class PaymentLineRepositoryImplTest {

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
    PaymentLineRepositoryImpl repository;

    @Autowired
    OrderJpaRepository orderJpaRepository;

    private OrderJpaEntity saveOrder() {
        return saveOrder(UUID.randomUUID().toString());
    }

    private OrderJpaEntity saveOrder(
            final String idempotencyKey
    ) {
        Order order = Order.create(
                IdempotencyKey.of(idempotencyKey),
                1L,
                1L,
                Money.of(50_000),
                LocalDateTime.now().plusMinutes(5)
        );
        return orderJpaRepository.save(OrderJpaEntity.from(order));
    }

    private PaymentLine aPaymentLine() {
        return PaymentLine.of(
                PaymentMethodCode.CREDIT_CARD,
                Money.of(50_000)
        );
    }

    @Nested
    @DisplayName("저장")
    class Save {

        @Test
        @DisplayName("결제 라인을 저장하면 ID가 부여된다")
        void save_persistsPaymentLine() {
            OrderJpaEntity order = saveOrder();
            PaymentLine line = aPaymentLine();

            PaymentLine saved = repository.save(line, order.getId());

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getMethod()).isEqualTo(PaymentMethodCode.CREDIT_CARD);
            assertThat(saved.getAmount()).isEqualTo(Money.of(50_000));
            assertThat(saved.getStatus()).isEqualTo(PaymentLineStatus.REQUESTED);
        }

        @Test
        @DisplayName("존재하지 않는 주문 ID로 저장하면 예외가 발생한다")
        void save_unknownOrder_throwsDomainException() {
            PaymentLine line = aPaymentLine();

            assertThatThrownBy(() -> repository.save(line, 999_999L))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("Order not found");
        }
    }

    @Nested
    @DisplayName("주문 ID로 조회")
    class FindByOrderId {

        @Test
        @DisplayName("저장한 결제 라인 2개를 주문 ID로 조회하면 모두 반환된다")
        void findByOrderId_returnsAllLines() {
            OrderJpaEntity order = saveOrder();
            repository.save(PaymentLine.of(PaymentMethodCode.YPOINT, Money.of(10_000)), order.getId());
            repository.save(PaymentLine.of(PaymentMethodCode.CREDIT_CARD, Money.of(40_000)), order.getId());

            List<PaymentLine> lines = repository.findByOrderId(order.getId());

            assertThat(lines).hasSize(2);
        }

        @Test
        @DisplayName("존재하지 않는 주문 ID로 조회하면 빈 목록을 반환한다")
        void findByOrderId_unknownOrder_returnsEmpty() {
            List<PaymentLine> lines = repository.findByOrderId(999_999L);

            assertThat(lines).isEmpty();
        }
    }

    @Nested
    @DisplayName("ID로 조회")
    class FindById {

        @Test
        @DisplayName("저장한 결제 라인을 ID로 조회하면 필드가 일치한다")
        void findById_returnsLine() {
            OrderJpaEntity order = saveOrder();
            PaymentLine saved = repository.save(aPaymentLine(), order.getId());

            Optional<PaymentLine> found = repository.findById(saved.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(saved.getId());
            assertThat(found.get().getMethod()).isEqualTo(PaymentMethodCode.CREDIT_CARD);
            assertThat(found.get().getAmount()).isEqualTo(Money.of(50_000));
            assertThat(found.get().getStatus()).isEqualTo(PaymentLineStatus.REQUESTED);
        }

        @Test
        @DisplayName("존재하지 않는 ID로 조회하면 빈 값을 반환한다")
        void findById_nonExisting_returnsEmpty() {
            Optional<PaymentLine> found = repository.findById(999_999L);

            assertThat(found).isEmpty();
        }
    }
}
