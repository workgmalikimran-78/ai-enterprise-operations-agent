package com.ai.aiops.api.dto;

import java.time.Instant;
import java.util.UUID;

import com.ai.aiops.domain.FactLogVolumeHourly;

public record FactLogVolumeHourlyResponse(
        Instant hourBucket,
        UUID applicationId,
        String applicationName,
        String level,
        int eventCount
) {
    public static FactLogVolumeHourlyResponse from(FactLogVolumeHourly f, String applicationName) {
        return new FactLogVolumeHourlyResponse(f.getHourBucket(), f.getApplicationId(), applicationName,
                f.getLevel(), f.getEventCount());
    }
}
