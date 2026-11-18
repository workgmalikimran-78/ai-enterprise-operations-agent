package com.ai.aiops.agent;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Evidence gathered by {@link InvestigationAgent} for a single incident. This is the only
 * material fed to the RCA agent's prompt - every field here is either a verified database fact
 * or an explicit statement that data is unavailable; nothing is inferred or fabricated at this
 * layer. All string content from logs is opaque data, never treated as instructions.
 */
public record InvestigationContext(
        UUID incidentId,
        String incidentTitle,
        String incidentSeverity,
        String applicationName,
        List<AnomalySummary> anomalies,
        List<LogSummary> recentErrorLogs,
        List<String> correlatedTraceIds,
        List<HistoricalIncidentSummary> historicalIncidents,
        List<RunbookSummary> relevantRunbooks,
        List<String> evidenceGaps
) {
    public record AnomalySummary(
            String detectionType,
            String severity,
            String description,
            Instant windowStart,
            Instant windowEnd,
            Map<String, Object> metadata
    ) {
    }

    public record LogSummary(
            Instant timestamp,
            String level,
            String traceId,
            String message,
            String exceptionType,
            String exceptionMessage
    ) {
    }

    public record HistoricalIncidentSummary(
            String title,
            String severity,
            String status,
            Instant openedAt,
            Instant resolvedAt
    ) {
    }

    public record RunbookSummary(
            String name,
            String triggerPattern,
            Map<String, Object> steps
    ) {
    }
}
