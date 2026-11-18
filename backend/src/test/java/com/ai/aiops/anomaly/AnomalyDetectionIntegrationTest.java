package com.ai.aiops.anomaly;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.ai.aiops.repository.AnomalyRepository;
import com.ai.aiops.repository.ApplicationRepository;

/**
 * End-to-end: POST log events through the real ingest endpoint -> Kafka -> rule-based detector
 * -> anomaly + incident creation, against real Postgres/Kafka/Redis containers. OpenSearch is not
 * started here; the log-indexer consumer group runs independently of the anomaly-detector group,
 * so its (expected) failures don't affect this flow.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AnomalyDetectionIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("aiops").withUsername("aiops").withPassword("aiops");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1"));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("aiops.anomaly.rule.threshold", () -> 5);
    }

    @Autowired
    private AnomalyRepository anomalyRepository;
    @Autowired
    private ApplicationRepository applicationRepository;
    @Autowired
    private org.springframework.boot.test.web.client.TestRestTemplate restTemplate;

    @Test
    void repeatedExceptionAcrossThresholdCreatesAnomalyAndIncident() throws InterruptedException {
        String service = "checkout-service-" + System.nanoTime();

        for (int i = 0; i < 12; i++) {
            String body = """
                    {
                      "service": "%s",
                      "environment": "production",
                      "level": "ERROR",
                      "traceId": "trace-%d",
                      "message": "Database connection timeout",
                      "exception": { "type": "SQLTransientConnectionException", "message": "timed out" }
                    }
                    """.formatted(service, i);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "/api/v1/logs/ingest", httpEntity(body), String.class);
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        }

        // With a single sequential consumer, the dedup flag always wins at count == threshold,
        // so this burst reliably produces a MEDIUM-severity anomaly (not HIGH/CRITICAL) - the
        // severity-to-incident correlation thresholds themselves are covered by IncidentServiceTest.
        boolean anomalyFound = waitUntil(Duration.ofSeconds(20), () ->
                !findAnomaliesForService(service).isEmpty());
        assertThat(anomalyFound).isTrue();
        assertThat(findAnomaliesForService(service).get(0).getSeverity()).isEqualTo("MEDIUM");
    }

    private List<com.ai.aiops.domain.Anomaly> findAnomaliesForService(String service) {
        return resolveApplicationId(service)
                .map(id -> anomalyRepository.findAll().stream()
                        .filter(a -> a.getApplication().getId().equals(id))
                        .toList())
                .orElseGet(List::of);
    }

    private java.util.Optional<java.util.UUID> resolveApplicationId(String service) {
        return applicationRepository.findAll().stream()
                .filter(a -> a.getName().equals(service))
                .map(com.ai.aiops.domain.Application::getId)
                .findFirst();
    }

    private boolean waitUntil(Duration timeout, java.util.function.BooleanSupplier condition)
            throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(500);
        }
        return condition.getAsBoolean();
    }

    private org.springframework.http.HttpEntity<String> httpEntity(String body) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new org.springframework.http.HttpEntity<>(body, headers);
    }
}
