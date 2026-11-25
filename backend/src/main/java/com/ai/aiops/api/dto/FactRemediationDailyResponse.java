package com.ai.aiops.api.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.ai.aiops.domain.FactRemediationDaily;

public record FactRemediationDailyResponse(
        LocalDate dateId,
        UUID applicationId,
        String applicationName,
        String actionType,
        int executionsTotal,
        int executionsSucceeded,
        int executionsFailed
) {
    public static FactRemediationDailyResponse from(FactRemediationDaily f, String applicationName) {
        return new FactRemediationDailyResponse(f.getDateId(), f.getApplicationId(), applicationName,
                f.getActionType(), f.getExecutionsTotal(), f.getExecutionsSucceeded(), f.getExecutionsFailed());
    }
}
