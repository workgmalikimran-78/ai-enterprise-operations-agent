package com.ai.aiops.api.dto;

import java.time.Instant;
import java.util.UUID;

import com.ai.aiops.domain.Anomaly;

public record AnomalyResponse(
        UUID id,
        UUID applicationId,
        String applicationName,
        String detectionType,
        String status,
        String severity,
        String description,
        Instant windowStart,
        Instant windowEnd,
        Instant createdAt
) {
    public static AnomalyResponse from(Anomaly a) {
        return new AnomalyResponse(
                a.getId(),
                a.getApplication().getId(),
                a.getApplication().getName(),
                a.getDetectionType(),
                a.getStatus(),
                a.getSeverity(),
                a.getDescription(),
                a.getWindowStart(),
                a.getWindowEnd(),
                a.getCreatedAt()
        );
    }
}
