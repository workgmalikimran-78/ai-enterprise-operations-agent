package com.ai.aiops.domain;

import java.time.LocalDate;
import java.util.UUID;

import com.ai.aiops.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Warehouse fact row: remediation execution outcomes for one (date, application, actionType) grouping. */
@Entity
@Table(name = "fact_remediation_daily", schema = "analytics")
@Getter
@Setter
@NoArgsConstructor
public class FactRemediationDaily extends BaseEntity {

    @Column(name = "date_id", nullable = false)
    private LocalDate dateId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "action_type", nullable = false)
    private String actionType;

    @Column(name = "executions_total", nullable = false)
    private Integer executionsTotal = 0;

    @Column(name = "executions_succeeded", nullable = false)
    private Integer executionsSucceeded = 0;

    @Column(name = "executions_failed", nullable = false)
    private Integer executionsFailed = 0;
}
