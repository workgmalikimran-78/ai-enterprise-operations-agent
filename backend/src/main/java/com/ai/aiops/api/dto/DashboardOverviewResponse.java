package com.ai.aiops.api.dto;

public record DashboardOverviewResponse(
        long totalIncidents,
        long criticalIncidents,
        long activeAnomalies,
        long resolvedIncidents,
        Double mttdSeconds,
        Double mttrSeconds,
        long aiInvestigations,
        long aiInvestigationFailures
) {
}
