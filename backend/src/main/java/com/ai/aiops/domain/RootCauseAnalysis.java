package com.ai.aiops.domain;

import java.math.BigDecimal;
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

/** Schema-complete shell; business logic (AI-driven RCA) lands in Phase 4. */
@Entity
@Table(name = "root_cause_analyses")
@Getter
@Setter
@NoArgsConstructor
public class RootCauseAnalysis extends BaseCreatedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Column(nullable = false, columnDefinition = "text")
    private String summary;

    @Column(nullable = false)
    private BigDecimal confidence;

    @Column(name = "llm_provider")
    private String llmProvider;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_response")
    private Map<String, Object> rawResponse;
}
