package com.ai.aiops.domain;

import java.time.Instant;
import java.util.UUID;

import com.ai.aiops.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Warehouse fact row: log event counts for one (hour, application, level) grouping. */
@Entity
@Table(name = "fact_log_volume_hourly", schema = "analytics")
@Getter
@Setter
@NoArgsConstructor
public class FactLogVolumeHourly extends BaseEntity {

    @Column(name = "hour_bucket", nullable = false)
    private Instant hourBucket;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(nullable = false)
    private String level;

    @Column(name = "event_count", nullable = false)
    private Integer eventCount = 0;
}
