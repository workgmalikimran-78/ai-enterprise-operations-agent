package com.ai.aiops.warehouse;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.ai.aiops.domain.EtlRunLog;
import com.ai.aiops.domain.FactIncidentDaily;
import com.ai.aiops.repository.DataQualityCheckRepository;
import com.ai.aiops.repository.FactIncidentDailyRepository;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;

/**
 * End-to-end: seeds an OLTP incident directly, runs WarehouseEtlJob once against real Postgres +
 * LocalStack (S3-only) containers, and asserts the warehouse fact table, the bronze lake object,
 * and the data quality check results all reflect it. LocalStack stands in for MinIO here only
 * because MinIO's own image stopped being pullable anonymously mid-build (see DataLakeConfig's
 * class comment) - the app talks to the generic AWS S3 SDK either way.
 */
@Testcontainers
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@ActiveProfiles("test")
class WarehouseEtlJobIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("aiops").withUsername("aiops").withPassword("aiops");

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3"))
            .withServices(LocalStackContainer.Service.S3);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("aiops.lake.endpoint", () -> localstack.getEndpoint().toString());
        registry.add("aiops.lake.access-key", localstack::getAccessKey);
        registry.add("aiops.lake.secret-key", localstack::getSecretKey);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private WarehouseEtlJob warehouseEtlJob;
    @Autowired
    private FactIncidentDailyRepository factIncidentDailyRepository;
    @Autowired
    private DataQualityCheckRepository dataQualityCheckRepository;
    @Autowired
    private S3Client s3Client;

    @Test
    void etlRunAggregatesIncidentsIntoWarehouseAndWritesBronze() {
        UUID tenantId = insertTenant();
        UUID applicationId = insertApplication(tenantId, "checkout-service-" + System.nanoTime());
        Instant openedAt = Instant.now().minusSeconds(3600);
        Instant resolvedAt = Instant.now().minusSeconds(1800);
        insertIncident(tenantId, applicationId, "HIGH", openedAt, resolvedAt);

        EtlRunLog runLog = warehouseEtlJob.runNow();

        assertThat(runLog.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(runLog.getRowsExtracted()).isGreaterThan(0);
        assertThat(runLog.getRowsLoaded()).isGreaterThan(0);

        java.util.List<FactIncidentDaily> facts = factIncidentDailyRepository
                .findByApplicationIdAndDateIdBetweenOrderByDateIdAsc(applicationId, LocalDate.now().minusDays(1),
                        LocalDate.now().plusDays(1));
        assertThat(facts).isNotEmpty();
        assertThat(facts.stream().mapToInt(f -> f.getIncidentsOpened() + f.getIncidentsResolved()).sum())
                .isGreaterThan(0);
        // PII-safe by construction: the fact row carries counts, not the incident title.
        assertThat(facts).allSatisfy(f -> assertThat(f.getSeverity()).isEqualTo("HIGH"));

        assertThat(dataQualityCheckRepository.findByEtlRunId(runLog.getId())).hasSize(4)
                .allSatisfy(c -> assertThat(c.getStatus()).isIn("PASS", "WARN"));

        var bronzeObjects = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                .bucket("aiops-lake").prefix("bronze/incidents/").build());
        assertThat(bronzeObjects.contents()).isNotEmpty();
    }

    private UUID insertTenant() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO tenants (id, name, slug) VALUES (?, ?, ?)", id, "Test Tenant",
                "test-tenant-" + id);
        return id;
    }

    private UUID insertApplication(UUID tenantId, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO applications (id, tenant_id, name, slug) VALUES (?, ?, ?, ?)", id,
                tenantId, name, name);
        return id;
    }

    private void insertIncident(UUID tenantId, UUID applicationId, String severity, Instant openedAt,
                                 Instant resolvedAt) {
        jdbcTemplate.update("""
                INSERT INTO incidents (id, tenant_id, application_id, title, status, severity, opened_at, resolved_at)
                VALUES (?, ?, ?, ?, 'RESOLVED', ?, ?, ?)
                """, UUID.randomUUID(), tenantId, applicationId, "Test incident", severity,
                Timestamp.from(openedAt), Timestamp.from(resolvedAt));
    }
}
