package com.ai.aiops.kafka;

import java.util.UUID;

public record IncidentEventMessage(
        String type, // INCIDENT_OPENED | INCIDENT_ANOMALY_LINKED
        UUID incidentId,
        UUID applicationId,
        UUID anomalyId,
        String severity
) {
}
