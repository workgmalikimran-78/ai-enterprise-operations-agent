package com.ai.aiops.logs;

import java.time.Instant;

/**
 * Canonical, validated representation of an incoming log line, independent of persistence
 * concerns. All string fields here are treated as opaque data by every downstream consumer -
 * never interpreted, templated, or executed.
 */
public record NormalizedLogEvent(
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
