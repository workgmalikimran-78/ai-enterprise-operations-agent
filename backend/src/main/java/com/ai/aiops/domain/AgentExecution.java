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

/** Schema-complete shell; populated once the agent orchestration layer exists in Phase 4+. */
@Entity
@Table(name = "agent_executions")
@Getter
@Setter
@NoArgsConstructor
public class AgentExecution extends BaseCreatedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id")
    private Incident incident;

    @Column(name = "agent_type", nullable = false)
    private String agentType;

    @Column(nullable = false)
    private String status = "PENDING";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    private Map<String, Object> input;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    private Map<String, Object> output;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
