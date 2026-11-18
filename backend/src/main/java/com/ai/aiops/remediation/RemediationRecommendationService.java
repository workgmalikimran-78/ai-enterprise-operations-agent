package com.ai.aiops.remediation;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ai.aiops.agent.RcaOutput;
import com.ai.aiops.api.dto.RemediationRecommendationResponse;
import com.ai.aiops.approval.ApprovalService;
import com.ai.aiops.common.exception.ResourceNotFoundException;
import com.ai.aiops.domain.AgentExecution;
import com.ai.aiops.domain.Incident;
import com.ai.aiops.domain.RemediationRecommendation;
import com.ai.aiops.domain.RootCauseAnalysis;
import com.ai.aiops.repository.AgentExecutionRepository;
import com.ai.aiops.repository.RemediationRecommendationRepository;
import com.ai.aiops.repository.RootCauseAnalysisRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RemediationRecommendationService {

    private final RootCauseAnalysisRepository rcaRepository;
    private final RemediationRecommendationRepository recommendationRepository;
    private final RemediationRecommendationAgent agent;
    private final ApprovalService approvalService;
    private final AgentExecutionRepository agentExecutionRepository;
    private final ObjectMapper objectMapper;

    public RemediationRecommendationService(RootCauseAnalysisRepository rcaRepository,
                                             RemediationRecommendationRepository recommendationRepository,
                                             RemediationRecommendationAgent agent, ApprovalService approvalService,
                                             AgentExecutionRepository agentExecutionRepository,
                                             ObjectMapper objectMapper) {
        this.rcaRepository = rcaRepository;
        this.recommendationRepository = recommendationRepository;
        this.agent = agent;
        this.approvalService = approvalService;
        this.agentExecutionRepository = agentExecutionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RemediationRecommendationResponse recommend(UUID incidentId) {
        RootCauseAnalysis rca = rcaRepository.findFirstByIncidentIdOrderByCreatedAtDesc(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No root cause analysis found for incident " + incidentId
                                + " - run POST /investigate first"));
        Incident incident = rca.getIncident();
        RcaOutput rcaOutput = objectMapper.convertValue(rca.getRawResponse(), RcaOutput.class);

        AgentExecution execution = startExecution(incident);
        RemediationOutput output;
        try {
            output = agent.recommend(rcaOutput, incident.getTitle(), incident.getSeverity(),
                    incident.getApplication().getName());
        } catch (RuntimeException e) {
            failExecution(execution, e.getMessage());
            throw e;
        }

        RemediationActionType actionType = RemediationActionType.fromString(output.actionType())
                .orElse(RemediationActionType.INVESTIGATE_FURTHER);

        RemediationRecommendation recommendation = new RemediationRecommendation();
        recommendation.setRootCauseAnalysis(rca);
        recommendation.setActionType(actionType.name());
        recommendation.setDescription(output.description());
        recommendation.setRiskLevel(actionType.riskLevel());
        recommendation.setRequiredApprovalLevel(actionType.requiredApprovalLevel());
        recommendation.setExpectedOutcome(output.expectedOutcome());
        recommendation.setBusinessImpact(output.businessImpact());
        recommendation.setRollbackStrategy(output.rollbackStrategy());
        recommendation.setStatus("PROPOSED");
        RemediationRecommendation saved = recommendationRepository.save(recommendation);

        completeExecution(execution, Map.of(
                "actionType", actionType.name(),
                "riskLevel", actionType.riskLevel(),
                "requiredApprovalLevel", actionType.requiredApprovalLevel()
        ));

        if (!"NONE".equals(actionType.requiredApprovalLevel())) {
            approvalService.createApprovalRequest(saved);
            saved.setStatus("APPROVAL_REQUESTED");
            saved = recommendationRepository.save(saved);
        }

        return RemediationRecommendationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<RemediationRecommendationResponse> listForIncident(UUID incidentId) {
        return recommendationRepository.findByRootCauseAnalysisIncidentIdOrderByCreatedAtDesc(incidentId).stream()
                .map(RemediationRecommendationResponse::from)
                .toList();
    }

    private AgentExecution startExecution(Incident incident) {
        AgentExecution execution = new AgentExecution();
        execution.setIncident(incident);
        execution.setAgentType("REMEDIATION");
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
