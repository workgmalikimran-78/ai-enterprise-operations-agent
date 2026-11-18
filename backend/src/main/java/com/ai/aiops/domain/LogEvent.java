package com.ai.aiops.domain;

import java.time.Instant;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.ai.aiops.common.BaseCreatedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "log_events")
@Getter
@Setter
@NoArgsConstructor
public class LogEvent extends BaseCreatedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "environment_id", nullable = false)
    private Environment environment;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(nullable = false)
    private String service;

    @Column(nullable = false)
    private String level;

    @Column(name = "trace_id")
    private String traceId;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "exception_type")
    private String exceptionType;

    @Column(name = "exception_message", columnDefinition = "text")
    private String exceptionMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload")
    private Map<String, Object> rawPayload;
}
