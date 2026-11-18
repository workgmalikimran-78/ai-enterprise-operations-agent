package com.ai.aiops.api.dto;

import java.time.Instant;
import java.util.UUID;

import com.ai.aiops.domain.ApprovalRequest;

public record ApprovalResponse(
        UUID id,
        UUID remediationRecommendationId,
        String status,
        String decidedByEmail,
        Instant decidedAt,
        Instant expiresAt,
        String rejectionReason,
        Instant createdAt
) {
    public static ApprovalResponse from(ApprovalRequest a) {
        return new ApprovalResponse(
                a.getId(),
                a.getRemediationRecommendation().getId(),
                a.getStatus(),
                a.getDecidedBy() != null ? a.getDecidedBy().getEmail() : null,
                a.getDecidedAt(),
                a.getExpiresAt(),
                a.getRejectionReason(),
                a.getCreatedAt()
        );
    }
}
