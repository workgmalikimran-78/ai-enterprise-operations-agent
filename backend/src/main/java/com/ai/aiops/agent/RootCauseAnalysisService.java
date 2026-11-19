package com.ai.aiops.agent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ai.aiops.api.dto.RcaResponse;
import com.ai.aiops.common.exception.ResourceNotFoundException;
import com.ai.aiops.domain.AgentExecution;
import com.ai.aiops.domain.Incident;
import com.ai.aiops.domain.RootCauseAnalysis;
import com.ai.aiops.repository.AgentExecutionRepository;
import com.ai.aiops.repository.IncidentRepository;
import com.ai.aiops.repository.RootCauseAnalysisRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Orchestrates the investigation -> RCA pipeline for a single incident: gather evidence, call the
 * LLM, persist the result, and record each agent's execution for the audit trail. The two agents
 * stay deliberately simple, synchronous, bounded steps - there is no open-ended agent loop or
 * unbounded tool-calling here, matching the "controlled orchestration" requirement.
 */
@Service
public class RootCauseAnalysisService {

    private final IncidentRepository incidentRepository;
    private final InvestigationAgent investigationAgent;
    private final RootCauseAnalysisAgent rcaAgent;
    private final RootCauseAnalysisRepository rcaRepository;
    private final AgentExecutionRepository agentExecutionRepository;
    private final ObjectMapper objectMapper;

    @Value("${aiops.llm.provider:openai}")
    private String llmProvider;

    public RootCauseAnalysisService(IncidentRepository incidentRepository, InvestigationAgent investigationAgent,
                                     RootCauseAnalysisAgent rcaAgent, RootCauseAnalysisRepository rcaRepository,
                                     AgentExecutionRepository agentExecutionRepository, ObjectMapper objectMapper) {
        this.incidentRepository = incidentRepository;
        this.investigationAgent = investigationAgent;
        this.rcaAgent = rcaAgent;
        this.rcaRepository = rcaRepository;
        this.agentExecutionRepository = agentExecutionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RcaResponse investigate(UUID incidentId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + incidentId));

        AgentExecution investigationExecution = startExecution("INVESTIGATION", incident);
        InvestigationContext context;
        try {
            context = investigationAgent.gatherContext(incident);
            completeExecution(investigationExecution, Map.of(
                    "anomaliesConsidered", context.anomalies().size(),
                    "logsConsidered", context.recentErrorLogs().size(),
                    "historicalIncidentsConsidered", context.historicalIncidents().size(),
                    "runbooksMatched", context.relevantRunbooks().size()
            ));
        } catch (RuntimeException e) {
            failExecution(investigationExecution, e.getMessage());
            throw e;
        }

        AgentExecution rcaExecution = startExecution("ROOT_CAUSE_ANALYSIS", incident);
        RcaOutput output;
        try {
            output = rcaAgent.analyze(context);
        } catch (RuntimeException e) {
            failExecution(rcaExecution, e.getMessage());
            throw e;
        }

        Map<String, Object> outputMap = objectMapper.convertValue(output, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
        });
        completeExecution(rcaExecution, outputMap);

        RootCauseAnalysis analysis = new RootCauseAnalysis();
        analysis.setIncident(incident);
        analysis.setSummary(output.summary());
        analysis.setConfidence(clampConfidence(output.confidence()));
        analysis.setLlmProvider(llmProvider);
        analysis.setRawResponse(outputMap);
        RootCauseAnalysis saved = rcaRepository.save(analysis);

        if ("OPEN".equals(incident.getStatus())) {
            incident.setStatus("INVESTIGATING");
            incidentRepository.save(incident);
        }

        return RcaResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public RcaResponse getLatestAnalysis(UUID incidentId) {
        RootCauseAnalysis analysis = rcaRepository.findFirstByIncidentIdOrderByCreatedAtDesc(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No root cause analysis found for incident: " + incidentId));
        return RcaResponse.from(analysis);
    }

    private BigDecimal clampConfidence(double confidence) {
        double clamped = Math.max(0.0, Math.min(1.0, confidence));
        return BigDecimal.valueOf(clamped).setScale(3, java.math.RoundingMode.HALF_UP);
    }

    private AgentExecution startExecution(String agentType, Incident incident) {
        AgentExecution execution = new AgentExecution();
        execution.setIncident(incident);
        execution.setAgentType(agentType);
        execution.setStatus("RUNNING");
        execution.setStartedAt(Instant.now());
        return agentExecutionRepository.save(execution);
    }

    private void completeExecution(AgentExecution execution, Map<String, Object> output) {
        execution.setStatus("SUCCEEDED");
        execution.setOutput(output);
        execution.setCompletedAt(Instant.now());
        agentExecutionRepository.save(execution);
    }

    private void failExecution(AgentExecution execution, String errorMessage) {
        execution.setStatus("FAILED");
        execution.setOutput(Map.of("error", errorMessage == null ? "unknown error" : errorMessage));
        execution.setCompletedAt(Instant.now());
        agentExecutionRepository.save(execution);
    }
}
