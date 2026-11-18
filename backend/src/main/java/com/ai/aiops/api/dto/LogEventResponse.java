package com.ai.aiops.api.dto;

import java.time.Instant;
import java.util.UUID;

import com.ai.aiops.domain.LogEvent;

public record LogEventResponse(
        UUID id,
        Instant timestamp,
        String service,
        String environment,
        String level,
        String traceId,
        String message,
        String exceptionType,
        String exceptionMessage,
        Instant createdAt
) {
    public static LogEventResponse from(LogEvent event) {
        return new LogEventResponse(
                event.getId(),
                event.getTimestamp(),
                event.getService(),
                event.getEnvironment().getName(),
                event.getLevel(),
                event.getTraceId(),
                event.getMessage(),
                event.getExceptionType(),
                event.getExceptionMessage(),
                event.getCreatedAt()
        );
    }
}
