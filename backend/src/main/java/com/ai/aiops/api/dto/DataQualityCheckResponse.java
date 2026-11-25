package com.ai.aiops.api.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.ai.aiops.domain.DataQualityCheck;

public record DataQualityCheckResponse(
        UUID id,
        UUID etlRunId,
        String checkName,
        String datasetName,
        Instant runAt,
        String status,
        Long rowCount,
        Map<String, Object> details
) {
    public static DataQualityCheckResponse from(DataQualityCheck c) {
        return new DataQualityCheckResponse(c.getId(), c.getEtlRunId(), c.getCheckName(), c.getDatasetName(),
                c.getRunAt(), c.getStatus(), c.getRowCount(), c.getDetails());
    }
}
