package com.ai.aiops.kafka;

import java.time.Instant;
import java.util.UUID;

public record AnomalyEventMessage(
        UUID anomalyId,
        UUID applicationId,
        String detectionType,
        String severity,
        String description,
        Instant windowStart,
        Instant windowEnd
) {
}
