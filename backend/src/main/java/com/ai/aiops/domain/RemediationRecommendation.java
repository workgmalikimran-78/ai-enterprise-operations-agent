package com.ai.aiops.domain;

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
@Table(name = "remediation_recommendations")
@Getter
@Setter
@NoArgsConstructor
public class RemediationRecommendation extends BaseCreatedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "root_cause_analysis_id", nullable = false)
    private RootCauseAnalysis rootCauseAnalysis;

    @Column(name = "action_type", nullable = false)
    private String actionType;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "risk_level", nullable = false)
    private String riskLevel;

    @Column(name = "expected_outcome", columnDefinition = "text")
    private String expectedOutcome;

    @Column(name = "business_impact", columnDefinition = "text")
    private String businessImpact;

    @Column(name = "rollback_strategy", columnDefinition = "text")
    private String rollbackStrategy;

    /** Deterministic, server-derived from actionType - never trusted from LLM output. */
    @Column(name = "required_approval_level", nullable = false)
    private String requiredApprovalLevel = "HIGH";

    @Column(nullable = false)
    private String status = "PROPOSED";
}
