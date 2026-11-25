package com.ai.aiops.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.ai.aiops.domain.FactIncidentDaily;

public record FactIncidentDailyResponse(
        LocalDate dateId,
        UUID applicationId,
        String applicationName,
        String severity,
        int incidentsOpened,
        int incidentsResolved,
        BigDecimal avgMttrSeconds
) {
    public static FactIncidentDailyResponse from(FactIncidentDaily f, String applicationName) {
        return new FactIncidentDailyResponse(f.getDateId(), f.getApplicationId(), applicationName, f.getSeverity(),
                f.getIncidentsOpened(), f.getIncidentsResolved(), f.getAvgMttrSeconds());
    }
}
