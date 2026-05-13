package com.flashsale.infrastructure.jpa.point;

import com.flashsale.application.booking.port.PointRepository;
import com.flashsale.domain.point.PointAccount;
import com.flashsale.domain.point.PointTx;
import com.flashsale.domain.shared.Money;
import com.flashsale.infrastructure.jpa.point.impl.PointRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(PointRepositoryImpl.class)
@DisplayName("PointRepository")
class PointRepositoryImplTest {

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
    PointRepository pointRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    static final long USER_ID = 1L;
    static final long INITIAL_BALANCE = 100_000L;

    @BeforeEach
    void resetBalance() {
        jdbcTemplate.update(
                "UPDATE point_account SET balance = ?, version = 0 WHERE user_id = ?",
                INITIAL_BALANCE, USER_ID
        );
    }

    @Nested
    @DisplayName("사용자별 포인트 계좌 조회")
    class FindByUserId {

        @Test
        @DisplayName("저장된 사용자의 포인트 잔액을 조회한다")
        void existingUser_returnsBalance() {
            PointAccount account = pointRepository.findByUserId(USER_ID).orElseThrow();

            assertThat(account.getUserId()).isEqualTo(USER_ID);
            assertThat(account.getBalance()).isEqualTo(INITIAL_BALANCE);
        }

        @Test
        @DisplayName("존재하지 않는 사용자로 조회하면 빈 값을 반환한다")
        void unknownUser_returnsEmpty() {
            assertThat(pointRepository.findByUserId(999_999L)).isEmpty();
        }
    }

    @Nested
    @DisplayName("포인트 계좌 저장")
    class Save {

        @Test
        @DisplayName("차감 후 저장하면 잔액이 줄어든다")
        void afterDeduct_balanceDecreases() {
            PointAccount account = pointRepository.findByUserId(USER_ID).orElseThrow();
            account.deduct(Money.of(10_000));

            pointRepository.save(account);

            PointAccount reloaded = pointRepository.findByUserId(USER_ID).orElseThrow();
            assertThat(reloaded.getBalance()).isEqualTo(90_000L);
        }

        @Test
        @DisplayName("환불 후 저장하면 잔액이 늘어난다")
        void afterRefund_balanceIncreases() {
            PointAccount account = pointRepository.findByUserId(USER_ID).orElseThrow();
            account.refund(Money.of(5_000));

            pointRepository.save(account);

            PointAccount reloaded = pointRepository.findByUserId(USER_ID).orElseThrow();
            assertThat(reloaded.getBalance()).isEqualTo(105_000L);
        }

        @Test
        @DisplayName("저장 후 버전이 증가한다")
        void afterSave_versionIncreases() {
            PointAccount account = pointRepository.findByUserId(USER_ID).orElseThrow();
            long initialVersion = account.getVersion();
            account.deduct(Money.of(1_000));

            pointRepository.save(account);

            PointAccount reloaded = pointRepository.findByUserId(USER_ID).orElseThrow();
            assertThat(reloaded.getVersion()).isGreaterThan(initialVersion);
        }

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        @DisplayName("두 트랜잭션이 동시에 같은 계좌를 수정하면 나중에 커밋한 쪽에서 낙관적 락 예외가 발생한다")
        void concurrentUpdate_throwsOptimisticLockException() throws InterruptedException {
            int threadCount = 2;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readLatch = new CountDownLatch(threadCount);
            CountDownLatch writeLatch = new CountDownLatch(1);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger conflictCount = new AtomicInteger(0);
            TransactionTemplate tx = new TransactionTemplate(transactionManager);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        tx.execute(status -> {
                            PointAccount account = pointRepository.findByUserId(USER_ID).orElseThrow();
                            readLatch.countDown();
                            try {
                                writeLatch.await();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            account.deduct(Money.of(1_000));
                            pointRepository.save(account);
                            return null;
                        });
                        successCount.incrementAndGet();
                    } catch (ObjectOptimisticLockingFailureException e) {
                        conflictCount.incrementAndGet();
                    }
                });
            }

            readLatch.await();
            writeLatch.countDown();
            executor.shutdown();
            executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);

            assertThat(successCount.get()).isEqualTo(1);
            assertThat(conflictCount.get()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("포인트 거래 내역 저장")
    class SaveTx {

        @Test
        @DisplayName("사용 거래 내역을 저장하면 ID가 부여된다")
        void useType_assignsId() {
            PointTx tx = PointTx.of(
                    USER_ID,
                    1L,
                    PointTx.Type.USE,
                    Money.of(10_000)
            );

            PointTx saved = pointRepository.saveTx(tx);

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getType()).isEqualTo(PointTx.Type.USE);
            assertThat(saved.getAmount().amount()).isEqualTo(10_000L);
        }

        @Test
        @DisplayName("환불 거래 내역을 저장하면 ID가 부여된다")
        void refundType_assignsId() {
            PointTx tx = PointTx.of(
                    USER_ID,
                    2L,
                    PointTx.Type.REFUND,
                    Money.of(5_000)
            );

            PointTx saved = pointRepository.saveTx(tx);

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getType()).isEqualTo(PointTx.Type.REFUND);
            assertThat(saved.getAmount().amount()).isEqualTo(5_000L);
        }
    }
}