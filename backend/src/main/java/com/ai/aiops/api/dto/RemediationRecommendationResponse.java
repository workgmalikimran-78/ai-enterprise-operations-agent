package com.ai.aiops.api.dto;

import java.time.Instant;
import java.util.UUID;

import com.ai.aiops.domain.RemediationRecommendation;

public record RemediationRecommendationResponse(
        UUID id,
        UUID incidentId,
        String actionType,
        String description,
        String riskLevel,
        String requiredApprovalLevel,
        String expectedOutcome,
        String businessImpact,
        String rollbackStrategy,
        String status,
        Instant createdAt
) {
    public static RemediationRecommendationResponse from(RemediationRecommendation r) {
        return new RemediationRecommendationResponse(
                r.getId(),
                r.getRootCauseAnalysis().getIncident().getId(),
                r.getActionType(),
                r.getDescription(),
                r.getRiskLevel(),
                r.getRequiredApprovalLevel(),
                r.getExpectedOutcome(),
                r.getBusinessImpact(),
                r.getRollbackStrategy(),
                r.getStatus(),
                r.getCreatedAt()
        );
    }
}
