package com.ai.aiops.warehouse;

import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ai.aiops.repository.FactIncidentDailyRepository;
import com.ai.aiops.repository.FactLogVolumeHourlyRepository;
import com.ai.aiops.repository.FactRemediationDailyRepository;

/**
 * The "T" and "L" of the ETL/ELT pipeline: refreshes the warehouse's dimensions, then pushes
 * aggregation down into SQL (native upsert queries on the fact repositories) rather than pulling
 * rows into Java and summing them - the standard ELT pattern, and the same shape you'd use against
 * a real cloud warehouse, just with Postgres as the load target instead of Snowflake/BigQuery.
 */
@Component
public class WarehouseLoader {

    private final JdbcTemplate jdbcTemplate;
    private final FactIncidentDailyRepository factIncidentDailyRepository;
    private final FactRemediationDailyRepository factRemediationDailyRepository;
    private final FactLogVolumeHourlyRepository factLogVolumeHourlyRepository;

    public WarehouseLoader(JdbcTemplate jdbcTemplate, FactIncidentDailyRepository factIncidentDailyRepository,
                            FactRemediationDailyRepository factRemediationDailyRepository,
                            FactLogVolumeHourlyRepository factLogVolumeHourlyRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.factIncidentDailyRepository = factIncidentDailyRepository;
        this.factRemediationDailyRepository = factRemediationDailyRepository;
        this.factLogVolumeHourlyRepository = factLogVolumeHourlyRepository;
    }

    /** Refreshes dimensions then loads all three fact tables for [from, to). Returns rows affected (approximate). */
    @Transactional
    public long load(Instant from, Instant to) {
        refreshDimensions(from, to);
        long rows = 0;
        rows += factIncidentDailyRepository.upsertOpenedCounts(from, to);
        rows += factIncidentDailyRepository.upsertResolvedCounts(from, to);
        rows += factRemediationDailyRepository.upsertFromExecutions(from, to);
        rows += factLogVolumeHourlyRepository.upsertFromLogEvents(from, to);
        return rows;
    }

    private void refreshDimensions(Instant from, Instant to) {
        // dim_application: full refresh - it's small (one row per application), so a conformed
        // copy is just kept in sync wholesale rather than tracked incrementally (type-1 SCD).
        jdbcTemplate.update("""
                INSERT INTO analytics.dim_application (application_id, tenant_id, name)
                SELECT id, tenant_id, name FROM applications
                ON CONFLICT (application_id) DO UPDATE SET
                    tenant_id = EXCLUDED.tenant_id,
                    name = EXCLUDED.name
                """);

        // dim_date: extend with any day in [from, to] not already present.
        jdbcTemplate.update("""
                INSERT INTO analytics.dim_date (date_id, year, month, day, iso_week)
                SELECT d::date, extract(year FROM d)::int, extract(month FROM d)::int,
                       extract(day FROM d)::int, extract(week FROM d)::int
                FROM generate_series(date_trunc('day', ?::timestamptz), date_trunc('day', ?::timestamptz),
                                      interval '1 day') d
                ON CONFLICT (date_id) DO NOTHING
                """, Timestamp.from(from), Timestamp.from(to));
    }
}
