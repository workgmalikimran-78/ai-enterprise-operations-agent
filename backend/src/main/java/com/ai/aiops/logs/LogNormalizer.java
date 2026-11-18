package com.ai.aiops.logs;

import java.time.Instant;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.ai.aiops.api.dto.LogIngestRequest;
import com.ai.aiops.common.exception.ValidationException;

/**
 * Pure normalization logic for incoming log payloads. Deliberately has no Spring/persistence
 * dependencies so it can be unit tested in isolation.
 */
@Component
public class LogNormalizer {

    private static final Set<String> VALID_LEVELS = Set.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL");
    private static final int MAX_MESSAGE_LENGTH = 8000;
    private static final int MAX_EXCEPTION_MESSAGE_LENGTH = 4000;

    public NormalizedLogEvent normalize(LogIngestRequest request) {
        String level = request.level().trim().toUpperCase();
        if (!VALID_LEVELS.contains(level)) {
            throw new ValidationException("Unrecognized log level: " + request.level());
        }

        Instant timestamp = request.timestamp() != null ? request.timestamp() : Instant.now();

        String exceptionType = null;
        String exceptionMessage = null;
        if (request.exception() != null) {
            exceptionType = request.exception().type();
            exceptionMessage = truncate(request.exception().message(), MAX_EXCEPTION_MESSAGE_LENGTH);
        }

        return new NormalizedLogEvent(
                timestamp,
                request.service().trim(),
                request.environment().trim(),
                level,
                request.traceId() != null ? request.traceId().trim() : null,
                truncate(request.message(), MAX_MESSAGE_LENGTH),
                exceptionType,
                exceptionMessage
        );
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
