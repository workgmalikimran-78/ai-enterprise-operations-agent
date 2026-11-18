package com.ai.aiops.api.dto;

import java.time.Instant;
import java.util.UUID;

import com.ai.aiops.domain.Incident;

public record IncidentResponse(
        UUID id,
        String title,
        String status,
        String severity,
        UUID applicationId,
        String applicationName,
        Instant openedAt,
        Instant resolvedAt
) {
    public static IncidentResponse from(Incident incident) {
        return new IncidentResponse(
                incident.getId(),
                incident.getTitle(),
                incident.getStatus(),
                incident.getSeverity(),
                incident.getApplication().getId(),
                incident.getApplication().getName(),
                incident.getOpenedAt(),
                incident.getResolvedAt()
        );
    }
}
