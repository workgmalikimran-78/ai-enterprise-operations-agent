package com.ai.aiops.api.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.ai.aiops.domain.AgentExecution;

public record AgentExecutionResponse(
        UUID id,
        UUID incidentId,
        String agentType,
        String status,
        Map<String, Object> input,
        Map<String, Object> output,
        Instant startedAt,
        Instant completedAt
) {
    public static AgentExecutionResponse from(AgentExecution e) {
        return new AgentExecutionResponse(
                e.getId(),
                e.getIncident() != null ? e.getIncident().getId() : null,
                e.getAgentType(),
                e.getStatus(),
                e.getInput(),
                e.getOutput(),
                e.getStartedAt(),
                e.getCompletedAt()
        );
    }
}
