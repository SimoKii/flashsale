package com.flashsale.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class IntegrationTestBase {

    static final MySQLContainer<?> mysql;
    static final GenericContainer<?> redis;

    static {
        mysql = new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("flashsale")
                .withUsername("flashsale")
                .withPassword("flashsalepw");
        redis = new GenericContainer<>("redis:7-alpine")
                .withExposedPorts(6379);
        mysql.start();
        redis.start();
    }

    @DynamicPropertySource
    static void properties(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("flashsale.booking-worker.product-ids", () -> "1");
        registry.add("spring.data.redis.timeout", () -> "2000ms");
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected StringRedisTemplate redisTemplate;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected void flushRedis() {
        redisTemplate.execute((RedisCallback<Object>) conn -> {
            conn.serverCommands().flushDb();
            return null;
        });
    }

    protected void truncateTables() {
        jdbcTemplate.update("SET FOREIGN_KEY_CHECKS = 0");
        jdbcTemplate.update("TRUNCATE TABLE payment_event");
        jdbcTemplate.update("TRUNCATE TABLE payment_reconcile_queue");
        jdbcTemplate.update("TRUNCATE TABLE payment_line");
        jdbcTemplate.update("TRUNCATE TABLE point_tx");
        jdbcTemplate.update("TRUNCATE TABLE orders");
        jdbcTemplate.update("SET FOREIGN_KEY_CHECKS = 1");
    }
}
