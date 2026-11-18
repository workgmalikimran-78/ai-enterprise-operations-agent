package com.ai.aiops.execution;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ai.aiops.api.dto.RemediationExecutionResponse;
import com.ai.aiops.common.exception.ResourceNotFoundException;
import com.ai.aiops.common.exception.ValidationException;
import com.ai.aiops.domain.AgentExecution;
import com.ai.aiops.domain.Incident;
import com.ai.aiops.domain.OperationalAudit;
import com.ai.aiops.domain.RemediationExecution;
import com.ai.aiops.domain.RemediationRecommendation;
import com.ai.aiops.execution.KubernetesOperationalTools.ApplicationHealth;
import com.ai.aiops.execution.KubernetesOperationalTools.PodInfo;
import com.ai.aiops.execution.KubernetesOperationalTools.RecentLogs;
import com.ai.aiops.execution.KubernetesOperationalTools.RestartResult;
import com.ai.aiops.execution.KubernetesOperationalTools.RollbackResult;
import com.ai.aiops.execution.KubernetesOperationalTools.ScaleResult;
import com.ai.aiops.execution.KubernetesOperationalTools.ServiceStatus;
import com.ai.aiops.execution.VerificationAgent.VerificationResult;
import com.ai.aiops.remediation.RemediationActionType;
import com.ai.aiops.repository.AgentExecutionRepository;
import com.ai.aiops.repository.IncidentRepository;
import com.ai.aiops.repository.OperationalAuditRepository;
import com.ai.aiops.repository.RemediationExecutionRepository;
import com.ai.aiops.repository.RemediationRecommendationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Executes an approved (or auto-allowed read-only) remediation against the real cluster.
 * Idempotency is enforced via a unique key derived from the recommendation ID, so calling
 * execute() twice for the same recommendation never runs the action twice - the second call just
 * returns the first call's recorded result.
 */
@Service
public class RemediationExecutionService {

    private final RemediationRecommendationRepository recommendationRepository;
    private final RemediationExecutionRepository executionRepository;
    private final IncidentRepository incidentRepository;
    private final AgentExecutionRepository agentExecutionRepository;
    private final OperationalAuditRepository auditRepository;
    private final KubernetesOperationalTools tools;
    private final VerificationAgent verificationAgent;
    private final ObjectMapper objectMapper;
    private final String defaultNamespace;

    public RemediationExecutionService(RemediationRecommendationRepository recommendationRepository,
                                        RemediationExecutionRepository executionRepository,
                                        IncidentRepository incidentRepository,
                                        AgentExecutionRepository agentExecutionRepository,
                                        OperationalAuditRepository auditRepository,
                                        KubernetesOperationalTools tools, VerificationAgent verificationAgent,
                                        ObjectMapper objectMapper,
                                        @Value("${aiops.execution.default-namespace:default}") String defaultNamespace) {
        this.recommendationRepository = recommendationRepository;
        this.executionRepository = executionRepository;
        this.incidentRepository = incidentRepository;
        this.agentExecutionRepository = agentExecutionRepository;
        this.auditRepository = auditRepository;
        this.tools = tools;
        this.verificationAgent = verificationAgent;
        this.objectMapper = objectMapper;
        this.defaultNamespace = defaultNamespace;
    }

    @Transactional
    public RemediationExecutionResponse execute(UUID recommendationId) {
        String idempotencyKey = recommendationId.toString();
        var existing = executionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return RemediationExecutionResponse.from(existing.get());
        }

        RemediationRecommendation recommendation = recommendationRepository.findById(recommendationId)
                .orElseThrow(() -> new ResourceNotFoundException("Remediation recommendation not found: "
                        + recommendationId));
        requireExecutable(recommendation);

        Incident incident = recommendation.getRootCauseAnalysis().getIncident();
        String deploymentName = incident.getApplication().getName();

        AgentExecution agentExecution = startAgentExecution(incident);
        RemediationExecution execution = new RemediationExecution();
        execution.setRemediationRecommendation(recommendation);
        execution.setIdempotencyKey(idempotencyKey);
        execution.setStartedAt(Instant.now());
        execution.setStatus("RUNNING");
        execution.setK8sJobRef(defaultNamespace + "/" + deploymentName);
        execution = executionRepository.save(execution);

        try {
            Object result = dispatch(recommendation, deploymentName);
            execution.setStatus("SUCCEEDED");
            execution.setResultLog(objectMapper.writeValueAsString(result));
            execution.setCompletedAt(Instant.now());
            executionRepository.save(execution);

            recommendation.setStatus("EXECUTED");
            recommendationRepository.save(recommendation);

            completeAgentExecution(agentExecution, Map.of("actionType", recommendation.getActionType(),
                    "status", "SUCCEEDED"));
            audit(incident, "REMEDIATION_EXECUTED", execution.getId(), recommendation.getActionType());

            return RemediationExecutionResponse.from(execution);
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            execution.setStatus("FAILED");
            execution.setResultLog(e.getMessage());
            execution.setCompletedAt(Instant.now());
            executionRepository.save(execution);
            failAgentExecution(agentExecution, e.getMessage());
            audit(incident, "REMEDIATION_EXECUTION_FAILED", execution.getId(), recommendation.getActionType());
            throw e instanceof RuntimeException re ? re : new IllegalStateException(e);
        }
    }

    @Transactional(readOnly = true)
    public RemediationExecutionResponse getStatus(UUID recommendationId) {
        return executionRepository.findFirstByRemediationRecommendationIdOrderByCreatedAtDesc(recommendationId)
                .map(RemediationExecutionResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No execution found for remediation recommendation " + recommendationId));
    }

    /** Runs every 5 minutes - verifies executions old enough to have a meaningful "after" window. */
    @Scheduled(fixedRateString = "${aiops.verification.check-interval-minutes:5}", timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    @Transactional
    public void verifyCompletedExecutions() {
        Instant cutoff = Instant.now().minus(java.time.Duration.ofMinutes(5));
        executionRepository.findByStatusAndVerifiedAtIsNullAndCompletedAtBefore("SUCCEEDED", cutoff)
                .forEach(this::verifyExecution);
    }

    private void verifyExecution(RemediationExecution execution) {
        Incident incident = execution.getRemediationRecommendation().getRootCauseAnalysis().getIncident();
        VerificationResult result = verificationAgent.verify(incident.getApplication().getId(),
                execution.getCompletedAt());

        execution.setVerifiedAt(Instant.now());
        executionRepository.save(execution);

        AgentExecution verificationExecution = startAgentExecution(incident);
        completeAgentExecution(verificationExecution, Map.of(
                "status", result.status(), "explanation", result.explanation(),
                "errorsBefore", result.errorsBefore(), "errorsAfter", result.errorsAfter()));

        switch (result.status()) {
            case "RESOLVED" -> {
                incident.setStatus("RESOLVED");
                incident.setResolvedAt(Instant.now());
            }
            case "PARTIALLY_RESOLVED" -> incident.setStatus("MITIGATED");
            default -> {
                // UNRESOLVED: leave incident status as-is: more agent-driven or human action is still needed.
            }
        }
        incidentRepository.save(incident);
    }

    private Object dispatch(RemediationRecommendation recommendation, String deploymentName) {
        RemediationActionType actionType = RemediationActionType.fromString(recommendation.getActionType())
                .orElse(RemediationActionType.INVESTIGATE_FURTHER);

        return switch (actionType) {
            case CHECK_APPLICATION_HEALTH -> (ApplicationHealth) tools.checkApplicationHealth(defaultNamespace,
                    deploymentName);
            case CHECK_SERVICE_STATUS -> (ServiceStatus) tools.getServiceStatus(defaultNamespace, deploymentName);
            case RETRIEVE_RECENT_LOGS -> retrieveLogsForFirstPod(deploymentName);
            case RESTART_DEPLOYMENT -> (RestartResult) tools.restartDeployment(defaultNamespace, deploymentName);
            case SCALE_DEPLOYMENT -> scaleUpByOne(deploymentName);
            case ROLLBACK_DEPLOYMENT -> (RollbackResult) tools.rollbackDeployment(defaultNamespace, deploymentName);
            case INVESTIGATE_FURTHER -> Map.of("message",
                    "No automated action available for this incident; manual investigation is required.");
        };
    }

    private RecentLogs retrieveLogsForFirstPod(String deploymentName) {
        PodInfo podInfo = tools.getPodInfo(defaultNamespace, deploymentName);
        if (podInfo.pods().isEmpty()) {
            throw new ResourceNotFoundException("No pods found for deployment " + deploymentName);
        }
        return tools.getRecentLogs(defaultNamespace, podInfo.pods().get(0).name(), 100);
    }

    private ScaleResult scaleUpByOne(String deploymentName) {
        ApplicationHealth health = tools.checkApplicationHealth(defaultNamespace, deploymentName);
        int target = Math.min(health.desiredReplicas() + 1, 20);
        return tools.scaleDeployment(defaultNamespace, deploymentName, target);
    }

    private void requireExecutable(RemediationRecommendation recommendation) {
        boolean readOnlyAutoAllowed = "NONE".equals(recommendation.getRequiredApprovalLevel())
                && "PROPOSED".equals(recommendation.getStatus());
        boolean approved = "APPROVED".equals(recommendation.getStatus());
        if (!readOnlyAutoAllowed && !approved) {
            throw new ValidationException("Remediation recommendation " + recommendation.getId()
                    + " is not executable (status: " + recommendation.getStatus()
                    + ", requiredApprovalLevel: " + recommendation.getRequiredApprovalLevel() + ")");
        }
    }

    private AgentExecution startAgentExecution(Incident incident) {
        AgentExecution execution = new AgentExecution();
        execution.setIncident(incident);
        execution.setAgentType("EXECUTION");
        execution.setStatus("RUNNING");
        execution.setStartedAt(Instant.now());
        return agentExecutionRepository.save(execution);
    }

    private void completeAgentExecution(AgentExecution execution, Map<String, Object> output) {
        execution.setStatus("SUCCEEDED");
        execution.setOutput(output);
        execution.setCompletedAt(Instant.now());
        agentExecutionRepository.save(execution);
    }

    private void failAgentExecution(AgentExecution execution, String errorMessage) {
        execution.setStatus("FAILED");
        execution.setOutput(Map.of("error", errorMessage == null ? "unknown error" : errorMessage));
        execution.setCompletedAt(Instant.now());
        agentExecutionRepository.save(execution);
    }

    private void audit(Incident incident, String action, UUID executionId, String actionType) {
        OperationalAudit auditEntry = new OperationalAudit();
        auditEntry.setTenant(incident.getTenant());
        auditEntry.setAction(action);
        auditEntry.setEntityType("REMEDIATION_EXECUTION");
        auditEntry.setEntityId(executionId);
        auditEntry.setDetails(Map.of("actionType", actionType, "incidentId", incident.getId().toString()));
        auditRepository.save(auditEntry);
    }
}
