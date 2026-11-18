package com.ai.aiops.kafka;

import java.time.Instant;
import java.util.UUID;

public record LogEventMessage(
        UUID logEventId,
        UUID applicationId,
        UUID environmentId,
        Instant timestamp,
        String service,
        String environment,
        String level,
        String traceId,
        String message,
        String exceptionType,
        String exceptionMessage
) {
}
