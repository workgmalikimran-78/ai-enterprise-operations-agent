package com.ai.aiops.api.dto;

import java.util.Map;

public record DashboardMetricsResponse(
        Map<String, Long> incidentsBySeverity,
        Map<String, Long> incidentsByStatus,
        Map<String, Long> anomaliesByDetectionType,
        Map<String, Long> agentExecutionsByType
) {
}
