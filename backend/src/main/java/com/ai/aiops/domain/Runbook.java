package com.ai.aiops.domain;

import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.ai.aiops.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Schema-complete shell; retrieved by the RCA/remediation agents starting Phase 4+. */
@Entity
@Table(name = "runbooks")
@Getter
@Setter
@NoArgsConstructor
public class Runbook extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false)
    private String name;

    @Column(name = "trigger_pattern")
    private String triggerPattern;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> steps;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
