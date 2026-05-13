package com.flashsale.infrastructure.jpa.product;

import com.flashsale.application.booking.port.ProductRepository;
import com.flashsale.domain.product.Product;
import com.flashsale.infrastructure.jpa.product.impl.ProductRepositoryImpl;
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

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(ProductRepositoryImpl.class)
@DisplayName("ProductRepository")
class ProductRepositoryImplTest {

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
    ProductRepository repository;

    @Nested
    @DisplayName("ID 조회")
    class FindById {

        @Test
        @DisplayName("저장된 상품을 ID로 조회하면 도메인 객체가 복원된다")
        void existingProduct_isRestoredAsDomainObject() {
            Product product = repository.findById(1L).orElseThrow();

            assertThat(product.getId()).isEqualTo(1L);
            assertThat(product.getName()).isNotBlank();
            assertThat(product.getPrice().amount()).isPositive();
        }

        @Test
        @DisplayName("체크인과 체크아웃 시각이 함께 복원된다")
        void stayPeriod_isRestored() {
            Product product = repository.findById(1L).orElseThrow();

            assertThat(product.getCheckinAt()).isNotNull();
            assertThat(product.getCheckoutAt()).isNotNull();
            assertThat(product.getCheckoutAt()).isAfter(product.getCheckinAt());
        }

        @Test
        @DisplayName("판매 오픈 시각 이후에 조회하면 판매 중으로 판별된다")
        void afterSaleOpen_isOnSale() {
            Product product = repository.findById(1L).orElseThrow();

            assertThat(product.isOnSale(LocalDateTime.now().plusYears(1))).isTrue();
        }

        @Test
        @DisplayName("판매 오픈 시각 이전에 조회하면 판매 중이 아닌 것으로 판별된다")
        void beforeSaleOpen_isNotOnSale() {
            Product product = repository.findById(1L).orElseThrow();

            assertThat(product.isOnSale(
                    LocalDateTime.of(
                            2000,
                            1,
                            1,
                            0,
                            0)
                    )).isFalse();
        }

        @Test
        @DisplayName("존재하지 않는 ID로 조회하면 빈 값을 반환한다")
        void nonExistingId_returnsEmpty() {
            assertThat(repository.findById(999_999L)).isEmpty();
        }
    }
}