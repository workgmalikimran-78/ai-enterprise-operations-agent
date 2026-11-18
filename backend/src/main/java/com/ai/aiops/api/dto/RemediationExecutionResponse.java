package com.ai.aiops.api.dto;

import java.time.Instant;
import java.util.UUID;

import com.ai.aiops.domain.RemediationExecution;

public record RemediationExecutionResponse(
        UUID id,
        UUID remediationRecommendationId,
        String status,
        String k8sJobRef,
        String resultLog,
        Instant startedAt,
        Instant completedAt,
        Instant verifiedAt
) {
    public static RemediationExecutionResponse from(RemediationExecution e) {
        return new RemediationExecutionResponse(
                e.getId(),
                e.getRemediationRecommendation() != null ? e.getRemediationRecommendation().getId() : null,
                e.getStatus(),
                e.getK8sJobRef(),
                e.getResultLog(),
                e.getStartedAt(),
                e.getCompletedAt(),
                e.getVerifiedAt()
        );
    }
}
