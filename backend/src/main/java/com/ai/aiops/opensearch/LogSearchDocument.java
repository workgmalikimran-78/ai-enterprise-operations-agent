package com.ai.aiops.opensearch;

import java.time.Instant;
import java.util.UUID;

import com.ai.aiops.kafka.LogEventMessage;

public record LogSearchDocument(
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
    public static LogSearchDocument from(LogEventMessage message) {
        return new LogSearchDocument(
                message.logEventId(),
                message.applicationId(),
                message.environmentId(),
                message.timestamp(),
                message.service(),
                message.environment(),
                message.level(),
                message.traceId(),
                message.message(),
                message.exceptionType(),
                message.exceptionMessage()
        );
    }
}
