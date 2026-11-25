package com.ai.aiops.api.dto;

import java.time.Instant;
import java.util.UUID;

import com.ai.aiops.domain.EtlRunLog;

public record EtlRunLogResponse(
        UUID id,
        String jobName,
        Instant startedAt,
        Instant completedAt,
        String status,
        Instant watermarkFrom,
        Instant watermarkTo,
        long rowsExtracted,
        long rowsLoaded,
        String errorMessage
) {
    public static EtlRunLogResponse from(EtlRunLog r) {
        return new EtlRunLogResponse(r.getId(), r.getJobName(), r.getStartedAt(), r.getCompletedAt(), r.getStatus(),
                r.getWatermarkFrom(), r.getWatermarkTo(), r.getRowsExtracted(), r.getRowsLoaded(),
                r.getErrorMessage());
    }
}
