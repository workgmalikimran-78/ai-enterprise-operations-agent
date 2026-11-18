package com.ai.aiops.api.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.ai.aiops.domain.OperationalAudit;

public record OperationalAuditResponse(
        UUID id,
        String actorEmail,
        String action,
        String entityType,
        UUID entityId,
        Map<String, Object> details,
        Instant createdAt
) {
    public static OperationalAuditResponse from(OperationalAudit a) {
        return new OperationalAuditResponse(
                a.getId(),
                a.getActorUser() != null ? a.getActorUser().getEmail() : "system",
                a.getAction(),
                a.getEntityType(),
                a.getEntityId(),
                a.getDetails(),
                a.getCreatedAt()
        );
    }
}
