package com.ai.aiops.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ai.aiops.domain.FactIncidentDaily;

public interface FactIncidentDailyRepository extends JpaRepository<FactIncidentDaily, UUID> {

    List<FactIncidentDaily> findByDateIdBetweenOrderByDateIdAsc(LocalDate from, LocalDate to);

    List<FactIncidentDaily> findByApplicationIdAndDateIdBetweenOrderByDateIdAsc(UUID applicationId, LocalDate from,
            LocalDate to);

    long countByDateId(LocalDate dateId);

    /**
     * Push-down ELT transform (1/2): upserts "opened that day" counts, grouped by the day the
     * incident was opened. The watermark filters on created_at (row creation - "is this incident
     * new since the last run"), not opened_at (business time - which day it belongs to) - opening
     * an incident sets both together, so this is equivalent in practice and keeps the watermark
     * semantics consistent with the bronze extraction step (see WarehouseEtlJob). Idempotent/
     * incremental - re-running the same [:from, :to) window twice double-counts; the ETL job only
     * ever calls this once per advancing watermark.
     */
    @Modifying
    @Query(value = """
            INSERT INTO analytics.fact_incident_daily (date_id, application_id, severity, incidents_opened)
            SELECT date_trunc('day', i.opened_at)::date, i.application_id, i.severity, count(*)
            FROM incidents i
            WHERE i.created_at >= :from AND i.created_at < :to
            GROUP BY 1, 2, 3
            ON CONFLICT (date_id, application_id, severity) DO UPDATE SET
                incidents_opened = fact_incident_daily.incidents_opened + EXCLUDED.incidents_opened,
                updated_at = now()
            """, nativeQuery = true)
    int upsertOpenedCounts(@Param("from") Instant from, @Param("to") Instant to);

    /**
     * Push-down ELT transform (2/2): upserts "resolved that day" counts + MTTR, grouped by the day
     * the incident was resolved (a different day than it was opened, in general - these two
     * upserts intentionally group independently rather than conflating the two dates). The
     * watermark filters on updated_at, not resolved_at - resolution is an UPDATE to a row that may
     * have been created long before this run's window, so created_at wouldn't catch it; updated_at
     * (Hibernate's @UpdateTimestamp on every save) is the correct "changed since last run" signal
     * for a mutable row. resolved_at itself is still used for the bucket date and the MTTR calc.
     * <p>
     * Known simplification: avg_mttr_seconds is overwritten with this run's window average rather
     * than folded into a true cumulative running average across every run that touches the same
     * calendar day - acceptable for a demo-scale ETL cadence, called out here rather than silently
     * glossed over.
     */
    @Modifying
    @Query(value = """
            INSERT INTO analytics.fact_incident_daily
                (date_id, application_id, severity, incidents_resolved, avg_mttr_seconds)
            SELECT date_trunc('day', i.resolved_at)::date, i.application_id, i.severity, count(*),
                   avg(extract(epoch FROM (i.resolved_at - i.opened_at)))
            FROM incidents i
            WHERE i.resolved_at IS NOT NULL AND i.updated_at >= :from AND i.updated_at < :to
            GROUP BY 1, 2, 3
            ON CONFLICT (date_id, application_id, severity) DO UPDATE SET
                incidents_resolved = fact_incident_daily.incidents_resolved + EXCLUDED.incidents_resolved,
                avg_mttr_seconds = EXCLUDED.avg_mttr_seconds,
                updated_at = now()
            """, nativeQuery = true)
    int upsertResolvedCounts(@Param("from") Instant from, @Param("to") Instant to);
}
