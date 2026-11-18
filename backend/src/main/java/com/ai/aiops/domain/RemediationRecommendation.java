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

/** Schema-complete shell; business logic (remediation proposals) lands in Phase 5. */
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
}
