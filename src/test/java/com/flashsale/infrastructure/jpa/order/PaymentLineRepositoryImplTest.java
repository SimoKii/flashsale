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

    private PaymentLine creditCardLine() {
        return PaymentLine.of(
                PaymentMethodCode.CREDIT_CARD,
                Money.of(50_000)
        );
    }

    private PaymentLine pointLine(
            final long amount
    ) {
        return PaymentLine.of(
                PaymentMethodCode.YPOINT,
                Money.of(amount)
        );
    }

    private PaymentLine approvedCreditCardLine(
            final String pgTxId
    ) {
        return PaymentLine.restore(
                null,
                PaymentMethodCode.CREDIT_CARD,
                Money.of(50_000),
                PaymentLineStatus.APPROVED,
                pgTxId,
                2
        );
    }

    private PaymentLine canceledCreditCardLine(
            final String pgTxId
    ) {
        return PaymentLine.restore(
                null,
                PaymentMethodCode.CREDIT_CARD,
                Money.of(50_000),
                PaymentLineStatus.CANCELED,
                pgTxId,
                2
        );
    }

    @Nested
    @DisplayName("저장")
    class Save {

        @Test
        @DisplayName("결제 라인을 저장하면 ID가 부여된다")
        void newLine_assignsId() {
            OrderJpaEntity order = saveOrder();
            PaymentLine line = creditCardLine();

            PaymentLine saved = repository.save(line, order.getId());

            assertThat(saved.getId()).isNotNull();
        }

        @Test
        @DisplayName("저장된 결제 라인은 입력한 필드를 모두 보존한다")
        void newLine_preservesAllFields() {
            OrderJpaEntity order = saveOrder();

            PaymentLine saved = repository.save(creditCardLine(), order.getId());

            assertThat(saved.getMethod()).isEqualTo(PaymentMethodCode.CREDIT_CARD);
            assertThat(saved.getAmount()).isEqualTo(Money.of(50_000));
            assertThat(saved.getStatus()).isEqualTo(PaymentLineStatus.REQUESTED);
            assertThat(saved.getSequence()).isEqualTo(2);
        }

        @Test
        @DisplayName("포인트 결제 라인은 sequence가 1이다")
        void pointLine_hasSequenceOne() {
            OrderJpaEntity order = saveOrder();

            PaymentLine saved = repository.save(pointLine(10_000), order.getId());

            assertThat(saved.getSequence()).isEqualTo(1);
        }

        @Test
        @DisplayName("같은 주문에 여러 결제 라인을 저장할 수 있다")
        void multipleLines_canBeSavedToSameOrder() {
            OrderJpaEntity order = saveOrder();

            PaymentLine point = repository.save(pointLine(10_000), order.getId());
            PaymentLine card = repository.save(creditCardLine(), order.getId());

            assertThat(point.getId()).isNotEqualTo(card.getId());
        }

        @Test
        @DisplayName("승인 상태의 결제 라인을 저장하면 PG 거래 ID가 보존된다")
        void approvedLine_preservesPgTxId() {
            OrderJpaEntity order = saveOrder();

            PaymentLine saved = repository.save(approvedCreditCardLine("pg-tx-123"), order.getId());

            assertThat(saved.getStatus()).isEqualTo(PaymentLineStatus.APPROVED);
            assertThat(saved.getPgTxId()).isEqualTo("pg-tx-123");
        }

        @Test
        @DisplayName("존재하지 않는 주문 ID로 저장하면 예외가 발생한다")
        void unknownOrder_throwsException() {
            PaymentLine line = creditCardLine();

            assertThatThrownBy(() -> repository.save(line, 999_999L))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("Order not found");
        }
    }

    @Nested
    @DisplayName("주문 ID로 조회")
    class FindByOrderId {

        @Test
        @DisplayName("저장된 결제 라인 2개를 주문 ID로 조회하면 모두 반환된다")
        void multipleLines_returnsAll() {
            OrderJpaEntity order = saveOrder();
            repository.save(pointLine(10_000), order.getId());
            repository.save(PaymentLine.of(PaymentMethodCode.CREDIT_CARD, Money.of(40_000)), order.getId());

            List<PaymentLine> lines = repository.findByOrderId(order.getId());

            assertThat(lines).hasSize(2);
        }

        @Test
        @DisplayName("복합 결제 라인은 sequence 순서로 정렬되어 반환된다")
        void compositeLines_returnedSortedBySequence() {
            OrderJpaEntity order = saveOrder();
            repository.save(PaymentLine.of(PaymentMethodCode.CREDIT_CARD, Money.of(40_000)), order.getId());
            repository.save(pointLine(10_000), order.getId());

            List<PaymentLine> lines = repository.findByOrderId(order.getId());

            assertThat(lines).hasSize(2);
            assertThat(lines.get(0).getSequence()).isEqualTo(1);
            assertThat(lines.get(0).getMethod()).isEqualTo(PaymentMethodCode.YPOINT);
            assertThat(lines.get(1).getSequence()).isEqualTo(2);
            assertThat(lines.get(1).getMethod()).isEqualTo(PaymentMethodCode.CREDIT_CARD);
        }

        @Test
        @DisplayName("다른 주문의 결제 라인은 반환되지 않는다")
        void otherOrdersLines_areNotReturned() {
            OrderJpaEntity order1 = saveOrder();
            OrderJpaEntity order2 = saveOrder();
            repository.save(creditCardLine(), order1.getId());
            repository.save(creditCardLine(), order2.getId());

            List<PaymentLine> lines = repository.findByOrderId(order1.getId());

            assertThat(lines).hasSize(1);
        }

        @Test
        @DisplayName("결제 라인이 없는 주문을 조회하면 빈 목록을 반환한다")
        void orderWithoutLines_returnsEmpty() {
            OrderJpaEntity order = saveOrder();

            List<PaymentLine> lines = repository.findByOrderId(order.getId());

            assertThat(lines).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 주문 ID로 조회하면 빈 목록을 반환한다")
        void unknownOrder_returnsEmpty() {
            List<PaymentLine> lines = repository.findByOrderId(999_999L);

            assertThat(lines).isEmpty();
        }
    }

    @Nested
    @DisplayName("ID로 조회")
    class FindById {

        @Test
        @DisplayName("저장된 결제 라인을 ID로 조회하면 모든 필드가 보존된다")
        void existingLine_preservesAllFields() {
            OrderJpaEntity order = saveOrder();
            PaymentLine saved = repository.save(creditCardLine(), order.getId());

            Optional<PaymentLine> found = repository.findById(saved.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(saved.getId());
            assertThat(found.get().getMethod()).isEqualTo(PaymentMethodCode.CREDIT_CARD);
            assertThat(found.get().getAmount()).isEqualTo(Money.of(50_000));
            assertThat(found.get().getStatus()).isEqualTo(PaymentLineStatus.REQUESTED);
            assertThat(found.get().getSequence()).isEqualTo(2);
        }

        @Test
        @DisplayName("승인 상태로 저장된 결제 라인의 PG 거래 ID가 복원된다")
        void approvedLine_preservesPgTxId() {
            OrderJpaEntity order = saveOrder();
            PaymentLine saved = repository.save(approvedCreditCardLine("pg-tx-999"), order.getId());

            Optional<PaymentLine> found = repository.findById(saved.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getPgTxId()).isEqualTo("pg-tx-999");
            assertThat(found.get().getStatus()).isEqualTo(PaymentLineStatus.APPROVED);
        }

        @Test
        @DisplayName("존재하지 않는 ID로 조회하면 빈 값을 반환한다")
        void nonExistingId_returnsEmpty() {
            Optional<PaymentLine> found = repository.findById(999_999L);

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("상태별 영속화")
    class StatusPersistence {

        @Test
        @DisplayName("승인 상태의 결제 라인을 저장하면 상태가 보존된다")
        void approvedStatus_isPersisted() {
            OrderJpaEntity order = saveOrder();
            PaymentLine line = approvedCreditCardLine("pg-tx-001");

            PaymentLine saved = repository.save(line, order.getId());

            Optional<PaymentLine> found = repository.findById(saved.getId());
            assertThat(found).isPresent();
            assertThat(found.get().getStatus()).isEqualTo(PaymentLineStatus.APPROVED);
            assertThat(found.get().getPgTxId()).isEqualTo("pg-tx-001");
        }

        @Test
        @DisplayName("취소 상태의 결제 라인을 저장하면 상태가 보존된다")
        void canceledStatus_isPersisted() {
            OrderJpaEntity order = saveOrder();
            PaymentLine line = canceledCreditCardLine("pg-tx-002");

            PaymentLine saved = repository.save(line, order.getId());

            Optional<PaymentLine> found = repository.findById(saved.getId());
            assertThat(found).isPresent();
            assertThat(found.get().getStatus()).isEqualTo(PaymentLineStatus.CANCELED);
        }
    }
}