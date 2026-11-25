package com.ai.aiops.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ai.aiops.domain.FactRemediationDaily;

public interface FactRemediationDailyRepository extends JpaRepository<FactRemediationDaily, UUID> {

    List<FactRemediationDaily> findByDateIdBetweenOrderByDateIdAsc(LocalDate from, LocalDate to);

    /**
     * Push-down ELT transform: joins remediation_executions back to the incident that triggered
     * them (execution -> recommendation -> root cause analysis -> incident) purely to resolve
     * application_id - the warehouse never stores the free-text recommendation/RCA content itself.
     */
    @Modifying
    @Query(value = """
            INSERT INTO analytics.fact_remediation_daily
                (date_id, application_id, action_type, executions_total, executions_succeeded, executions_failed)
            SELECT
                date_trunc('day', re.completed_at)::date,
                i.application_id,
                rr.action_type,
                count(*),
                count(*) FILTER (WHERE re.status = 'SUCCEEDED'),
                count(*) FILTER (WHERE re.status = 'FAILED')
            FROM remediation_executions re
            JOIN remediation_recommendations rr ON rr.id = re.remediation_recommendation_id
            JOIN root_cause_analyses rca ON rca.id = rr.root_cause_analysis_id
            JOIN incidents i ON i.id = rca.incident_id
            WHERE re.completed_at IS NOT NULL AND re.completed_at >= :from AND re.completed_at < :to
            GROUP BY 1, 2, 3
            ON CONFLICT (date_id, application_id, action_type) DO UPDATE SET
                executions_total = fact_remediation_daily.executions_total + EXCLUDED.executions_total,
                executions_succeeded = fact_remediation_daily.executions_succeeded + EXCLUDED.executions_succeeded,
                executions_failed = fact_remediation_daily.executions_failed + EXCLUDED.executions_failed,
                updated_at = now()
            """, nativeQuery = true)
    int upsertFromExecutions(@Param("from") Instant from, @Param("to") Instant to);
}
