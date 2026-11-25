package com.ai.aiops.warehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ai.aiops.domain.DataQualityCheck;
import com.ai.aiops.domain.EtlRunLog;
import com.ai.aiops.repository.DataQualityCheckRepository;
import com.ai.aiops.repository.EtlRunLogRepository;

class DataQualityServiceTest {

    private final DataQualityCheckRepository checkRepository = mock(DataQualityCheckRepository.class);
    private final EtlRunLogRepository etlRunLogRepository = mock(EtlRunLogRepository.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

    private DataQualityService service() {
        when(checkRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jdbcTemplate.queryForObject(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(Long.class))).thenReturn(0L);
        return new DataQualityService(checkRepository, etlRunLogRepository, jdbcTemplate, 60);
    }

    @Test
    void completenessFailsWhenRowsWereExtractedButNothingWasLoaded() {
        when(etlRunLogRepository.findFirstByJobNameAndStatusOrderByWatermarkToDesc(WarehouseEtlJob.JOB_NAME,
                "SUCCEEDED")).thenReturn(Optional.empty());

        List<DataQualityCheck> results = service().runChecks(UUID.randomUUID(), 10, 0);

        DataQualityCheck completeness = results.stream()
                .filter(c -> c.getCheckName().equals("completeness_extraction_implies_load")).findFirst()
                .orElseThrow();
        assertThat(completeness.getStatus()).isEqualTo("FAIL");
    }

    @Test
    void completenessPassesWhenNothingWasExtracted() {
        when(etlRunLogRepository.findFirstByJobNameAndStatusOrderByWatermarkToDesc(WarehouseEtlJob.JOB_NAME,
                "SUCCEEDED")).thenReturn(Optional.empty());

        List<DataQualityCheck> results = service().runChecks(UUID.randomUUID(), 0, 0);

        DataQualityCheck completeness = results.stream()
                .filter(c -> c.getCheckName().equals("completeness_extraction_implies_load")).findFirst()
                .orElseThrow();
        assertThat(completeness.getStatus()).isEqualTo("PASS");
    }

    @Test
    void freshnessPassesOnFirstEverRun() {
        when(etlRunLogRepository.findFirstByJobNameAndStatusOrderByWatermarkToDesc(WarehouseEtlJob.JOB_NAME,
                "SUCCEEDED")).thenReturn(Optional.empty());

        List<DataQualityCheck> results = service().runChecks(UUID.randomUUID(), 5, 5);

        DataQualityCheck freshness = results.stream()
                .filter(c -> c.getCheckName().equals("freshness_etl_run_recent")).findFirst().orElseThrow();
        assertThat(freshness.getStatus()).isEqualTo("PASS");
    }

    @Test
    void freshnessWarnsWhenLastSuccessfulRunIsStale() {
        EtlRunLog staleRun = new EtlRunLog();
        staleRun.setCompletedAt(java.time.Instant.now().minus(java.time.Duration.ofHours(5)));
        when(etlRunLogRepository.findFirstByJobNameAndStatusOrderByWatermarkToDesc(WarehouseEtlJob.JOB_NAME,
                "SUCCEEDED")).thenReturn(Optional.of(staleRun));

        List<DataQualityCheck> results = service().runChecks(UUID.randomUUID(), 5, 5);

        DataQualityCheck freshness = results.stream()
                .filter(c -> c.getCheckName().equals("freshness_etl_run_recent")).findFirst().orElseThrow();
        assertThat(freshness.getStatus()).isEqualTo("WARN");
    }

    @Test
    void piiSafetyPassesBecauseFactEntitiesCarryNoFreeTextColumns() {
        when(etlRunLogRepository.findFirstByJobNameAndStatusOrderByWatermarkToDesc(WarehouseEtlJob.JOB_NAME,
                "SUCCEEDED")).thenReturn(Optional.empty());

        List<DataQualityCheck> results = service().runChecks(UUID.randomUUID(), 5, 5);

        DataQualityCheck piiSafety = results.stream()
                .filter(c -> c.getCheckName().equals("pii_safety_no_free_text_columns")).findFirst().orElseThrow();
        assertThat(piiSafety.getStatus()).isEqualTo("PASS");
        assertThat((List<?>) piiSafety.getDetails().get("violations")).isEmpty();
    }
}
