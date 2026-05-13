package com.flashsale.infrastructure.redis;

import com.flashsale.infrastructure.redis.config.RedisConfig;
import com.flashsale.infrastructure.redis.stock.impl.RedisStockAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(classes = {RedisAutoConfiguration.class, RedisConfig.class, RedisStockAdapter.class})
@DisplayName("RedisStockAdapter")
class RedisStockAdapterTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(
            final DynamicPropertyRegistry registry
    ) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedisStockAdapter adapter;

    static final long PRODUCT_ID = 1L;
    static final String STOCK_KEY = "stock:product:1";
    static final String HOLDERS_KEY = "holders:product:1";
    static final String TICKET_PROCESSED_KEY = "ticket_processed:product:1";

    @BeforeEach
    void setUp() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        redisTemplate.opsForValue().set(STOCK_KEY, "10");
    }

    @Nested
    @DisplayName("재고 점유")
    class Reserve {

        @Test
        @DisplayName("재고를 점유하면 남은 재고가 줄어든다")
        void reserve_decreasesRemaining() {
            int remaining = adapter.reserve(PRODUCT_ID, "ticket-001", 100L);

            assertThat(remaining).isEqualTo(9);
            assertThat(redisTemplate.opsForValue().get(STOCK_KEY)).isEqualTo("9");
        }

        @Test
        @DisplayName("재고를 점유하면 사용자가 holders에 등록된다")
        void reserve_addsUserToHolders() {
            adapter.reserve(PRODUCT_ID, "ticket-002", 100L);

            assertThat(redisTemplate.opsForZSet().score(HOLDERS_KEY, "100")).isNotNull();
        }

        @Test
        @DisplayName("재고를 점유하면 티켓이 처리 완료 집합에 등록된다")
        void reserve_addsTicketToProcessedSet() {
            adapter.reserve(PRODUCT_ID, "ticket-003", 100L);

            assertThat(redisTemplate.opsForSet().isMember(TICKET_PROCESSED_KEY, "ticket-003")).isTrue();
        }

        @Test
        @DisplayName("동일한 티켓으로 중복 점유하면 0을 반환하고 재고는 변하지 않는다")
        void duplicateTicket_returnsZeroWithoutChange() {
            adapter.reserve(PRODUCT_ID, "ticket-dup", 100L);

            int second = adapter.reserve(PRODUCT_ID, "ticket-dup", 100L);

            assertThat(second).isZero();
            assertThat(redisTemplate.opsForValue().get(STOCK_KEY)).isEqualTo("9");
        }

        @Test
        @DisplayName("재고가 매진되면 -1을 반환한다")
        void soldOut_returnsMinusOne() {
            redisTemplate.opsForValue().set(STOCK_KEY, "0");

            int result = adapter.reserve(PRODUCT_ID, "ticket-sold", 200L);

            assertThat(result).isEqualTo(-1);
        }

        @Test
        @DisplayName("매진 직전까지 점유하면 남은 재고가 0이 된다")
        void reserveUntilExhausted_remainingIsZero() {
            int lastRemaining = -1;
            for (int i = 0; i < 10; i++) {
                lastRemaining = adapter.reserve(PRODUCT_ID, "ticket-" + i, (long) (i + 1));
            }

            assertThat(lastRemaining).isZero();
            assertThat(redisTemplate.opsForValue().get(STOCK_KEY)).isEqualTo("0");
        }

        @Test
        @DisplayName("재고가 없는 상품에 점유 요청해도 -1을 반환한다")
        void unknownProduct_returnsMinusOne() {
            int result = adapter.reserve(999L, "ticket-x", 100L);

            assertThat(result).isEqualTo(-1);
        }
    }

    @Nested
    @DisplayName("재고 복구")
    class Restore {

        @Test
        @DisplayName("점유를 취소하면 재고가 복구된다")
        void restore_increasesStock() {
            adapter.reserve(PRODUCT_ID, "ticket-r01", 300L);

            adapter.restore(PRODUCT_ID, "ticket-r01", 300L);

            assertThat(redisTemplate.opsForValue().get(STOCK_KEY)).isEqualTo("10");
        }

        @Test
        @DisplayName("점유를 취소하면 사용자가 holders에서 제거된다")
        void restore_removesUserFromHolders() {
            adapter.reserve(PRODUCT_ID, "ticket-r02", 300L);

            adapter.restore(PRODUCT_ID, "ticket-r02", 300L);

            assertThat(redisTemplate.opsForZSet().score(HOLDERS_KEY, "300")).isNull();
        }

        @Test
        @DisplayName("여러 명이 점유한 상태에서 한 명만 취소하면 그 사용자만 제거된다")
        void restoreOneOfMultiple_removesOnlyTarget() {
            adapter.reserve(PRODUCT_ID, "ticket-multi-1", 100L);
            adapter.reserve(PRODUCT_ID, "ticket-multi-2", 200L);
            adapter.reserve(PRODUCT_ID, "ticket-multi-3", 300L);

            adapter.restore(PRODUCT_ID, "ticket-multi-2", 200L);

            assertThat(redisTemplate.opsForZSet().score(HOLDERS_KEY, "100")).isNotNull();
            assertThat(redisTemplate.opsForZSet().score(HOLDERS_KEY, "200")).isNull();
            assertThat(redisTemplate.opsForZSet().score(HOLDERS_KEY, "300")).isNotNull();
            assertThat(redisTemplate.opsForValue().get(STOCK_KEY)).isEqualTo("8");
        }
    }

    @Nested
    @DisplayName("잔여 재고 조회")
    class Remaining {

        @Test
        @DisplayName("초기 남은 재고는 설정된 값과 같다")
        void initialRemaining_equalsInitialValue() {
            assertThat(adapter.remaining(PRODUCT_ID)).isEqualTo(10);
        }

        @Test
        @DisplayName("점유 후 남은 재고가 줄어든다")
        void afterReserve_remainingDecreases() {
            adapter.reserve(PRODUCT_ID, "ticket-rem", 100L);

            assertThat(adapter.remaining(PRODUCT_ID)).isEqualTo(9);
        }

        @Test
        @DisplayName("재고 키가 없는 상품의 남은 재고는 0이다")
        void unknownProduct_returnsZero() {
            assertThat(adapter.remaining(999L)).isZero();
        }
    }
}