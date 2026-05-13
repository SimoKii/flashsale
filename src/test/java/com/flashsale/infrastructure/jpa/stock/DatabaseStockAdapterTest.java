package com.flashsale.infrastructure.jpa.stock;

import com.flashsale.application.booking.port.StockPort;
import com.flashsale.common.exception.DomainException;
import com.flashsale.infrastructure.jpa.stock.impl.DatabaseStockAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(DatabaseStockAdapter.class)
@DisplayName("DatabaseStockAdapter")
class DatabaseStockAdapterTest {

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
    StockPort stockPort;

    @Autowired
    StockJpaRepository stockJpaRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    static final long PRODUCT_ID = 1L;

    @BeforeEach
    void resetStock() {
        jdbcTemplate.update(
                "UPDATE stock SET reserved = 0, sold = 0, version = 0 WHERE product_id = ?",
                PRODUCT_ID
        );
    }

    @Nested
    @DisplayName("재고 점유")
    class Reserve {

        @Test
        @DisplayName("재고를 점유하면 남은 재고가 줄어든다")
        void reserve_decreasesRemaining() {
            int remaining = stockPort.reserve(PRODUCT_ID, "ticket-001", 1L);

            assertThat(remaining).isEqualTo(9);
            assertThat(stockJpaRepository.findByProductId(PRODUCT_ID).orElseThrow().getReserved()).isEqualTo(1);
        }

        @Test
        @DisplayName("재고가 매진되면 -1을 반환한다")
        void soldOut_returnsMinusOne() {
            for (int i = 0; i < 10; i++) {
                stockPort.reserve(PRODUCT_ID, "ticket-" + String.format("%03d", i), (long) (i + 1));
            }

            int result = stockPort.reserve(PRODUCT_ID, "ticket-999", 99L);

            assertThat(result).isEqualTo(-1);
        }

        @Test
        @DisplayName("매진 직전까지 점유하면 남은 재고가 0이 된다")
        void reserveUntilExhausted_remainingIsZero() {
            int lastRemaining = -1;
            for (int i = 0; i < 10; i++) {
                lastRemaining = stockPort.reserve(PRODUCT_ID, "ticket-" + i, (long) (i + 1));
            }

            assertThat(lastRemaining).isZero();
        }

        @Test
        @DisplayName("존재하지 않는 상품으로 점유하면 예외가 발생한다")
        void unknownProduct_throwsException() {
            assertThatThrownBy(() -> stockPort.reserve(999_999L, "ticket-x", 1L))
                    .isInstanceOf(DomainException.class).hasMessageContaining("Stock not found");
        }

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        @DisplayName("5명이 동시에 점유하면 정확히 5건만 성공한다")
        void concurrentReserve_underCapacity_allSucceed() throws InterruptedException {
            int threadCount = 5;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            IntStream.range(0, threadCount).forEach(i ->
                    executor.submit(() -> {
                        try {
                            int result = stockPort.reserve(PRODUCT_ID, "ticket-c" + i, (long) (i + 1));
                            if (result >= 0) successCount.incrementAndGet();
                        } finally {
                            latch.countDown();
                        }
                    })
            );

            latch.await();
            executor.shutdown();

            int reserved = stockJpaRepository.findByProductId(PRODUCT_ID).orElseThrow().getReserved();
            assertThat(reserved).isEqualTo(successCount.get());
            assertThat(reserved).isEqualTo(5);
        }

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        @DisplayName("재고를 초과하여 동시에 점유하면 초과분은 매진으로 거부된다")
        void concurrentReserve_exceedingTotal_rejectsSurplus() throws InterruptedException {
            int threadCount = 15;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger soldOutCount = new AtomicInteger(0);

            IntStream.range(0, threadCount).forEach(i ->
                    executor.submit(() -> {
                        try {
                            int result = stockPort.reserve(PRODUCT_ID, "ticket-x" + i, (long) (i + 1));
                            if (result >= 0) successCount.incrementAndGet();
                            else soldOutCount.incrementAndGet();
                        } finally {
                            latch.countDown();
                        }
                    })
            );

            latch.await();
            executor.shutdown();

            assertThat(successCount.get()).isEqualTo(10);
            assertThat(soldOutCount.get()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("점유 취소")
    class Restore {

        @Test
        @DisplayName("점유를 취소하면 점유 수량이 줄어든다")
        void restore_decreasesReserved() {
            stockPort.reserve(PRODUCT_ID, "ticket-r01", 1L);
            stockPort.reserve(PRODUCT_ID, "ticket-r02", 2L);

            stockPort.restore(PRODUCT_ID, "ticket-r01");

            assertThat(stockJpaRepository.findByProductId(PRODUCT_ID).orElseThrow().getReserved()).isEqualTo(1);
        }

        @Test
        @DisplayName("점유를 취소하면 남은 재고가 복구된다")
        void restore_increasesRemaining() {
            stockPort.reserve(PRODUCT_ID, "ticket-r03", 1L);

            stockPort.restore(PRODUCT_ID, "ticket-r03");

            assertThat(stockPort.remaining(PRODUCT_ID)).isEqualTo(10);
        }

        @Test
        @DisplayName("점유가 없는 상태에서 취소하면 예외가 발생한다")
        void noReservation_throwsException() {
            assertThatThrownBy(() -> stockPort.restore(PRODUCT_ID, "ticket-none"))
                    .isInstanceOf(DomainException.class).hasMessageContaining("Nothing to restore");
        }
    }

    @Nested
    @DisplayName("판매 확정")
    class Confirm {

        @Test
        @DisplayName("점유를 확정하면 점유는 줄고 판매는 늘어난다")
        void confirm_movesFromReservedToSold() {
            stockPort.reserve(PRODUCT_ID, "ticket-c01", 1L);

            stockPort.confirm(PRODUCT_ID, "ticket-c01");

            StockJpaEntity stock = stockJpaRepository.findByProductId(PRODUCT_ID).orElseThrow();
            assertThat(stock.getReserved()).isZero();
            assertThat(stock.getSold()).isEqualTo(1);
        }

        @Test
        @DisplayName("확정 후에도 남은 재고는 줄어든 상태를 유지한다")
        void afterConfirm_remainingStaysReduced() {
            stockPort.reserve(PRODUCT_ID, "ticket-c02", 1L);
            stockPort.confirm(PRODUCT_ID, "ticket-c02");

            assertThat(stockPort.remaining(PRODUCT_ID)).isEqualTo(9);
        }

        @Test
        @DisplayName("점유가 없는 상태에서 확정하면 예외가 발생한다")
        void noReservation_throwsException() {
            assertThatThrownBy(() -> stockPort.confirm(PRODUCT_ID, "ticket-none"))
                    .isInstanceOf(DomainException.class).hasMessageContaining("Nothing to confirm");
        }
    }

    @Nested
    @DisplayName("잔여 재고 조회")
    class Remaining {

        @Test
        @DisplayName("초기 남은 재고는 총량과 같다")
        void initialRemaining_equalsTotal() {
            assertThat(stockPort.remaining(PRODUCT_ID)).isEqualTo(10);
        }

        @Test
        @DisplayName("점유하면 남은 재고가 줄어든다")
        void afterReserve_remainingDecreases() {
            stockPort.reserve(PRODUCT_ID, "ticket-rem", 1L);

            assertThat(stockPort.remaining(PRODUCT_ID)).isEqualTo(9);
        }

        @Test
        @DisplayName("존재하지 않는 상품으로 조회하면 예외가 발생한다")
        void unknownProduct_throwsException() {
            assertThatThrownBy(() -> stockPort.remaining(999_999L))
                    .isInstanceOf(DomainException.class).hasMessageContaining("Stock not found");
        }
    }
}