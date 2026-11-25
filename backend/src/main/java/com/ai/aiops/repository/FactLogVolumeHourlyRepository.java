package com.ai.aiops.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ai.aiops.domain.FactLogVolumeHourly;

public interface FactLogVolumeHourlyRepository extends JpaRepository<FactLogVolumeHourly, UUID> {

    List<FactLogVolumeHourly> findByHourBucketBetweenOrderByHourBucketAsc(Instant from, Instant to);

    List<FactLogVolumeHourly> findByApplicationIdAndHourBucketBetweenOrderByHourBucketAsc(UUID applicationId,
            Instant from, Instant to);

    /** Push-down ELT transform: hourly event counts by application/level, grouped by event time. */
    @Modifying
    @Query(value = """
            INSERT INTO analytics.fact_log_volume_hourly (hour_bucket, application_id, level, event_count)
            SELECT date_trunc('hour', "timestamp"), application_id, level, count(*)
            FROM log_events
            WHERE "timestamp" >= :from AND "timestamp" < :to
            GROUP BY 1, 2, 3
            ON CONFLICT (hour_bucket, application_id, level) DO UPDATE SET
                event_count = fact_log_volume_hourly.event_count + EXCLUDED.event_count,
                updated_at = now()
            """, nativeQuery = true)
    int upsertFromLogEvents(@Param("from") Instant from, @Param("to") Instant to);
}
