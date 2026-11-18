package com.ai.aiops;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Loads the full Spring context against a real Postgres container, applying every Flyway
 * migration and validating every JPA entity mapping against the resulting schema in one shot.
 * Kafka listener containers are disabled here since there's no live broker - this test's job is
 * schema/mapping validation, not end-to-end messaging.
 */
@Testcontainers
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@ActiveProfiles("test")
class AiopsApplicationTests {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("aiops")
            .withUsername("aiops")
            .withPassword("aiops");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void contextLoadsAndFlywayMigrationsApplyCleanly() {
        // Intentionally empty: context refresh alone exercises Flyway + JPA validate.
    }
}
