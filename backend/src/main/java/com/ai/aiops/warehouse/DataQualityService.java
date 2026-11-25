package com.ai.aiops.warehouse;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.ai.aiops.domain.DataQualityCheck;
import com.ai.aiops.domain.FactIncidentDaily;
import com.ai.aiops.domain.FactLogVolumeHourly;
import com.ai.aiops.domain.FactRemediationDaily;
import com.ai.aiops.repository.DataQualityCheckRepository;
import com.ai.aiops.repository.EtlRunLogRepository;

import jakarta.persistence.Column;

/**
 * Runs four checks after every WarehouseEtlJob run and records one DataQualityCheck row each:
 * completeness (did extraction imply load), freshness (is the job keeping up with its own
 * schedule), referential integrity (do fact rows' dimension keys resolve), and PII-safety (do the
 * fact entities still carry zero free-text columns, enforced by reflection so a future change that
 * violates "PII-safe by construction" fails loudly here instead of silently).
 */
@Component
public class DataQualityService {

    private static final List<Class<?>> FACT_ENTITY_CLASSES =
            List.of(FactIncidentDaily.class, FactRemediationDaily.class, FactLogVolumeHourly.class);

    private final DataQualityCheckRepository checkRepository;
    private final EtlRunLogRepository etlRunLogRepository;
    private final JdbcTemplate jdbcTemplate;
    private final int intervalMinutes;

    public DataQualityService(DataQualityCheckRepository checkRepository, EtlRunLogRepository etlRunLogRepository,
                               JdbcTemplate jdbcTemplate,
                               @Value("${aiops.warehouse.etl.interval-minutes:60}") int intervalMinutes) {
        this.checkRepository = checkRepository;
        this.etlRunLogRepository = etlRunLogRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.intervalMinutes = intervalMinutes;
    }

    public List<DataQualityCheck> runChecks(UUID etlRunId, long rowsExtracted, long rowsLoaded) {
        List<DataQualityCheck> results = new ArrayList<>();
        results.add(completenessCheck(etlRunId, rowsExtracted, rowsLoaded));
        results.add(freshnessCheck(etlRunId));
        results.add(referentialIntegrityCheck(etlRunId));
        results.add(piiSafetyCheck(etlRunId));
        return results;
    }

    private DataQualityCheck completenessCheck(UUID etlRunId, long rowsExtracted, long rowsLoaded) {
        boolean pass = rowsExtracted == 0 || rowsLoaded > 0;
        return save(etlRunId, "completeness_extraction_implies_load", "warehouse.*", pass ? "PASS" : "FAIL",
                rowsLoaded, Map.of("rowsExtracted", rowsExtracted, "rowsLoaded", rowsLoaded));
    }

    private DataQualityCheck freshnessCheck(UUID etlRunId) {
        var lastSucceeded = etlRunLogRepository.findFirstByJobNameAndStatusOrderByWatermarkToDesc(
                WarehouseEtlJob.JOB_NAME, "SUCCEEDED");
        if (lastSucceeded.isEmpty()) {
            return save(etlRunId, "freshness_etl_run_recent", "etl_run_log", "PASS", null,
                    Map.of("note", "first successful run"));
        }
        Instant lastCompleted = lastSucceeded.get().getCompletedAt();
        long minutesSince = java.time.Duration.between(lastCompleted, Instant.now()).toMinutes();
        boolean pass = minutesSince <= intervalMinutes * 2L;
        return save(etlRunId, "freshness_etl_run_recent", "etl_run_log", pass ? "PASS" : "WARN", null,
                Map.of("minutesSinceLastSuccess", minutesSince, "expectedIntervalMinutes", intervalMinutes));
    }

    private DataQualityCheck referentialIntegrityCheck(UUID etlRunId) {
        Long orphanIncidentFacts = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM analytics.fact_incident_daily f
                WHERE NOT EXISTS (SELECT 1 FROM analytics.dim_application d WHERE d.application_id = f.application_id)
                   OR NOT EXISTS (SELECT 1 FROM analytics.dim_date d WHERE d.date_id = f.date_id)
                """, Long.class);
        Long orphanRemediationFacts = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM analytics.fact_remediation_daily f
                WHERE NOT EXISTS (SELECT 1 FROM analytics.dim_application d WHERE d.application_id = f.application_id)
                """, Long.class);
        Long orphanLogVolumeFacts = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM analytics.fact_log_volume_hourly f
                WHERE NOT EXISTS (SELECT 1 FROM analytics.dim_application d WHERE d.application_id = f.application_id)
                """, Long.class);
        long totalOrphans = nvl(orphanIncidentFacts) + nvl(orphanRemediationFacts) + nvl(orphanLogVolumeFacts);
        return save(etlRunId, "referential_integrity_fact_dimension_keys", "warehouse.fact_tables",
                totalOrphans == 0 ? "PASS" : "FAIL", totalOrphans,
                Map.of("orphanIncidentFacts", nvl(orphanIncidentFacts), "orphanRemediationFacts",
                        nvl(orphanRemediationFacts), "orphanLogVolumeFacts", nvl(orphanLogVolumeFacts)));
    }

    private DataQualityCheck piiSafetyCheck(UUID etlRunId) {
        List<String> violations = new ArrayList<>();
        for (Class<?> factClass : FACT_ENTITY_CLASSES) {
            for (Field field : factClass.getDeclaredFields()) {
                Column column = field.getAnnotation(Column.class);
                boolean isFreeText = column != null && column.columnDefinition() != null
                        && column.columnDefinition().toLowerCase().contains("text");
                boolean isUnboundedString = field.getType() == String.class
                        && (column == null || column.length() > 255);
                if (isFreeText || isUnboundedString) {
                    violations.add(factClass.getSimpleName() + "." + field.getName());
                }
            }
        }
        String status = violations.isEmpty() ? "PASS" : "FAIL";
        return save(etlRunId, "pii_safety_no_free_text_columns", "warehouse.fact_tables", status, null,
                Map.of("violations", violations));
    }

    private DataQualityCheck save(UUID etlRunId, String checkName, String datasetName, String status,
                                   Long rowCount, Map<String, Object> details) {
        DataQualityCheck check = new DataQualityCheck();
        check.setEtlRunId(etlRunId);
        check.setCheckName(checkName);
        check.setDatasetName(datasetName);
        check.setRunAt(Instant.now());
        check.setStatus(status);
        check.setRowCount(rowCount);
        check.setDetails(details);
        return checkRepository.save(check);
    }

    private static long nvl(Long value) {
        return value == null ? 0L : value;
    }
}
