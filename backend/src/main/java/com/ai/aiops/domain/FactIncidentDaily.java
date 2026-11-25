package com.ai.aiops.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.ai.aiops.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Warehouse fact row: incident counts and MTTR for one (date, application, severity) grouping.
 * Deliberately carries no free-text columns - only ids and aggregated numbers - so the warehouse
 * layer is PII-safe by construction regardless of what the OLTP log/incident text contains.
 */
@Entity
@Table(name = "fact_incident_daily", schema = "analytics")
@Getter
@Setter
@NoArgsConstructor
public class FactIncidentDaily extends BaseEntity {

    @Column(name = "date_id", nullable = false)
    private LocalDate dateId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(nullable = false)
    private String severity;

    @Column(name = "incidents_opened", nullable = false)
    private Integer incidentsOpened = 0;

    @Column(name = "incidents_resolved", nullable = false)
    private Integer incidentsResolved = 0;

    @Column(name = "avg_mttr_seconds")
    private BigDecimal avgMttrSeconds;
}
