package com.ai.aiops.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.ai.aiops.common.BaseCreatedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One row per data quality check per ETL run (completeness, freshness, referential integrity, PII-safety). */
@Entity
@Table(name = "data_quality_checks")
@Getter
@Setter
@NoArgsConstructor
public class DataQualityCheck extends BaseCreatedEntity {

    @Column(name = "etl_run_id")
    private UUID etlRunId;

    @Column(name = "check_name", nullable = false)
    private String checkName;

    @Column(name = "dataset_name", nullable = false)
    private String datasetName;

    @Column(name = "run_at", nullable = false)
    private Instant runAt;

    @Column(nullable = false)
    private String status;

    @Column(name = "row_count")
    private Long rowCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    private Map<String, Object> details;
}
