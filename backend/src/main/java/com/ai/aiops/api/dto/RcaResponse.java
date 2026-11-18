package com.ai.aiops.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.ai.aiops.domain.RootCauseAnalysis;

public record RcaResponse(
        UUID id,
        UUID incidentId,
        String summary,
        BigDecimal confidence,
        String llmProvider,
        Map<String, Object> details,
        Instant createdAt
) {
    public static RcaResponse from(RootCauseAnalysis analysis) {
        return new RcaResponse(
                analysis.getId(),
                analysis.getIncident().getId(),
                analysis.getSummary(),
                analysis.getConfidence(),
                analysis.getLlmProvider(),
                analysis.getRawResponse(),
                analysis.getCreatedAt()
        );
    }
}
