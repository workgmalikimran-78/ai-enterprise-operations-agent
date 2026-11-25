package com.ai.aiops.warehouse;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ai.aiops.domain.EtlRunLog;
import com.ai.aiops.repository.EtlRunLogRepository;

/**
 * Orchestrates one ETL/ELT run: extract OLTP rows since the last watermark, write them raw to the
 * bronze lake (full fidelity, including free text), aggregate them into the warehouse's fact
 * tables (counts/averages only - see WarehouseLoader), run data quality checks against the
 * result, and record the run. Runs on a schedule and can also be triggered on demand via
 * POST /api/v1/warehouse/etl/run.
 */
@Component
public class WarehouseEtlJob {

    private static final Logger log = LoggerFactory.getLogger(WarehouseEtlJob.class);
    public static final String JOB_NAME = "warehouse_etl";

    private final EtlRunLogRepository etlRunLogRepository;
    private final WarehouseLoader warehouseLoader;
    private final DataLakeWriter dataLakeWriter;
    private final DataQualityService dataQualityService;
    private final JdbcTemplate jdbcTemplate;
    private final int lookbackDaysOnFirstRun;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public WarehouseEtlJob(EtlRunLogRepository etlRunLogRepository, WarehouseLoader warehouseLoader,
                            DataLakeWriter dataLakeWriter, DataQualityService dataQualityService,
                            JdbcTemplate jdbcTemplate,
                            @Value("${aiops.warehouse.etl.lookback-days-on-first-run:7}") int lookbackDaysOnFirstRun) {
        this.etlRunLogRepository = etlRunLogRepository;
        this.warehouseLoader = warehouseLoader;
        this.dataLakeWriter = dataLakeWriter;
        this.dataQualityService = dataQualityService;
        this.jdbcTemplate = jdbcTemplate;
        this.lookbackDaysOnFirstRun = lookbackDaysOnFirstRun;
    }

    /**
     * Runs on the configured interval (default 60 minutes). The first automatic execution waits
     * one full interval rather than firing immediately at startup (Spring's default for
     * fixedRate) - a rolling deploy restarts the app far more often than the data actually needs
     * re-extracting, so an immediate first run would hammer Postgres on every pod restart for no
     * benefit. Use POST /api/v1/warehouse/etl/run for an on-demand run right after a fresh deploy.
     */
    @Scheduled(initialDelayString = "${aiops.warehouse.etl.interval-minutes:60}",
            fixedRateString = "${aiops.warehouse.etl.interval-minutes:60}", timeUnit = TimeUnit.MINUTES)
    public void run() {
        runNow();
    }

    /**
     * Synchronous trigger, used by both the scheduled call above and
     * POST /api/v1/warehouse/etl/run. Guarded by {@link #running} so a manual trigger can never
     * overlap with the scheduled run (or another manual trigger) - without this, two concurrent
     * runs could both compute the same "no prior successful run" watermark and race to record
     * EtlRunLog rows, making it ambiguous which run's result a caller actually gets back.
     */
    public EtlRunLog runNow() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Warehouse ETL job is already running");
        }
        try {
            Instant to = Instant.now();
            Instant from = determineWatermark(to);
            EtlRunLog runLog = startRun(from, to);

            try {
                long rowsExtracted = extractAndWriteBronze(from, to);
                long rowsLoaded = warehouseLoader.load(from, to);
                dataQualityService.runChecks(runLog.getId(), rowsExtracted, rowsLoaded);
                completeRun(runLog, rowsExtracted, rowsLoaded);
                log.info("Warehouse ETL run {} succeeded: {} rows extracted, {} rows loaded ({} to {})",
                        runLog.getId(), rowsExtracted, rowsLoaded, from, to);
                return runLog;
            } catch (RuntimeException e) {
                failRun(runLog, e);
                log.error("Warehouse ETL run {} failed", runLog.getId(), e);
                throw e;
            }
        } finally {
            running.set(false);
        }
    }

    private Instant determineWatermark(Instant to) {
        return etlRunLogRepository.findFirstByJobNameAndStatusOrderByWatermarkToDesc(JOB_NAME, "SUCCEEDED")
                .map(EtlRunLog::getWatermarkTo)
                .orElseGet(() -> to.minus(Duration.ofDays(lookbackDaysOnFirstRun)));
    }

    /**
     * Raw extract-to-lake step. Deliberately uses created_at (row/ingestion time, a simple
     * change-data-capture-style watermark) for all three datasets, distinct from the business
     * timestamps (opened_at/resolved_at, completed_at, event "timestamp") the warehouse load step
     * aggregates on - bronze answers "what rows appeared in this window", the warehouse answers
     * "what happened on which calendar day/hour".
     */
    private long extractAndWriteBronze(Instant from, Instant to) {
        long total = 0;
        total += extractDataset("log_events", """
                SELECT id, tenant_id, application_id, environment_id, "timestamp", service, level,
                       trace_id, message, exception_type, exception_message, created_at
                FROM log_events WHERE created_at >= ? AND created_at < ?
                """, from, to);
        total += extractDataset("incidents", """
                SELECT id, tenant_id, application_id, title, status, severity, opened_at, resolved_at, created_at
                FROM incidents WHERE created_at >= ? AND created_at < ?
                """, from, to);
        total += extractDataset("remediation_executions", """
                SELECT id, remediation_recommendation_id, status, idempotency_key, started_at,
                       completed_at, k8s_job_ref, created_at
                FROM remediation_executions WHERE created_at >= ? AND created_at < ?
                """, from, to);
        return total;
    }

    private long extractDataset(String dataset, String sql, Instant from, Instant to) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, Timestamp.from(from), Timestamp.from(to));
        if (!rows.isEmpty()) {
            dataLakeWriter.writeBronze(dataset, rows, from, to);
        }
        return rows.size();
    }

    private EtlRunLog startRun(Instant from, Instant to) {
        EtlRunLog runLog = new EtlRunLog();
        runLog.setJobName(JOB_NAME);
        runLog.setStartedAt(Instant.now());
        runLog.setStatus("RUNNING");
        runLog.setWatermarkFrom(from);
        runLog.setWatermarkTo(to);
        return etlRunLogRepository.save(runLog);
    }

    // Not @Transactional: called via self-invocation from run(), which would bypass the proxy
    // anyway. etlRunLogRepository.save() is already transactional on its own (Spring Data JPA
    // repository methods are), so each call here commits independently - exactly what's wanted
    // for a status/progress record that must survive even if a later step throws.
    private void completeRun(EtlRunLog runLog, long rowsExtracted, long rowsLoaded) {
        runLog.setStatus("SUCCEEDED");
        runLog.setRowsExtracted(rowsExtracted);
        runLog.setRowsLoaded(rowsLoaded);
        runLog.setCompletedAt(Instant.now());
        etlRunLogRepository.save(runLog);
    }

    private void failRun(EtlRunLog runLog, Exception e) {
        runLog.setStatus("FAILED");
        runLog.setErrorMessage(e.getMessage());
        runLog.setCompletedAt(Instant.now());
        etlRunLogRepository.save(runLog);
    }
}
