package com.ai.aiops.domain;

import java.time.Instant;

import com.ai.aiops.common.BaseCreatedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One row per WarehouseEtlJob run: watermark window, row counts, and outcome. */
@Entity
@Table(name = "etl_run_log")
@Getter
@Setter
@NoArgsConstructor
public class EtlRunLog extends BaseCreatedEntity {

    @Column(name = "job_name", nullable = false)
    private String jobName;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(nullable = false)
    private String status = "RUNNING";

    @Column(name = "watermark_from")
    private Instant watermarkFrom;

    @Column(name = "watermark_to")
    private Instant watermarkTo;

    @Column(name = "rows_extracted", nullable = false)
    private Long rowsExtracted = 0L;

    @Column(name = "rows_loaded", nullable = false)
    private Long rowsLoaded = 0L;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;
}
