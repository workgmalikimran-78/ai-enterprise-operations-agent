package com.ai.aiops.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ai.aiops.api.dto.RemediationExecutionResponse;
import com.ai.aiops.common.exception.ValidationException;
import com.ai.aiops.domain.AgentExecution;
import com.ai.aiops.domain.Application;
import com.ai.aiops.domain.Incident;
import com.ai.aiops.domain.RemediationExecution;
import com.ai.aiops.domain.RemediationRecommendation;
import com.ai.aiops.domain.RootCauseAnalysis;
import com.ai.aiops.domain.Tenant;
import com.ai.aiops.execution.KubernetesOperationalTools.ApplicationHealth;
import com.ai.aiops.repository.AgentExecutionRepository;
import com.ai.aiops.repository.IncidentRepository;
import com.ai.aiops.repository.OperationalAuditRepository;
import com.ai.aiops.repository.RemediationExecutionRepository;
import com.ai.aiops.repository.RemediationRecommendationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class RemediationExecutionServiceTest {

    @Mock
    private RemediationRecommendationRepository recommendationRepository;
    @Mock
    private RemediationExecutionRepository executionRepository;
    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private AgentExecutionRepository agentExecutionRepository;
    @Mock
    private OperationalAuditRepository auditRepository;
    @Mock
    private KubernetesOperationalTools tools;
    @Mock
    private VerificationAgent verificationAgent;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RemediationExecutionService service() {
        return new RemediationExecutionService(recommendationRepository, executionRepository, incidentRepository,
                agentExecutionRepository, auditRepository, tools, verificationAgent, objectMapper, "default");
    }

    private RemediationRecommendation recommendation(String actionType, String status, String approvalLevel) {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        Application application = new Application();
        application.setId(UUID.randomUUID());
        application.setTenant(tenant);
        application.setName("order-service");
        Incident incident = new Incident();
        incident.setId(UUID.randomUUID());
        incident.setTenant(tenant);
        incident.setApplication(application);
        RootCauseAnalysis rca = new RootCauseAnalysis();
        rca.setIncident(incident);

        RemediationRecommendation recommendation = new RemediationRecommendation();
        recommendation.setId(UUID.randomUUID());
        recommendation.setRootCauseAnalysis(rca);
        recommendation.setActionType(actionType);
        recommendation.setStatus(status);
        recommendation.setRequiredApprovalLevel(approvalLevel);
        return recommendation;
    }

    @Test
    void secondCallIsIdempotentAndDoesNotReinvokeTools() {
        RemediationRecommendation recommendation = recommendation("CHECK_APPLICATION_HEALTH", "PROPOSED", "NONE");
        RemediationExecution existing = new RemediationExecution();
        existing.setId(UUID.randomUUID());
        existing.setStatus("SUCCEEDED");
        existing.setIdempotencyKey(recommendation.getId().toString());

        when(executionRepository.findByIdempotencyKey(recommendation.getId().toString()))
                .thenReturn(Optional.of(existing));

        RemediationExecutionResponse response = service().execute(recommendation.getId());

        assertThat(response.id()).isEqualTo(existing.getId());
        verify(recommendationRepository, never()).findById(any());
        verify(tools, never()).checkApplicationHealth(anyString(), anyString());
    }

    @Test
    void rejectsExecutionOfUnapprovedHighRiskRecommendation() {
        RemediationRecommendation recommendation = recommendation("RESTART_DEPLOYMENT", "PROPOSED", "HIGH");
        when(executionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(recommendationRepository.findById(recommendation.getId())).thenReturn(Optional.of(recommendation));

        assertThatThrownBy(() -> service().execute(recommendation.getId()))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("not executable");

        verify(tools, never()).restartDeployment(anyString(), anyString());
    }

    @Test
    void executesReadOnlyActionAndMarksRecommendationExecuted() {
        RemediationRecommendation recommendation = recommendation("CHECK_APPLICATION_HEALTH", "PROPOSED", "NONE");
        when(executionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(recommendationRepository.findById(recommendation.getId())).thenReturn(Optional.of(recommendation));
        when(agentExecutionRepository.save(any(AgentExecution.class))).thenAnswer(inv -> inv.getArgument(0));
        when(executionRepository.save(any(RemediationExecution.class))).thenAnswer(inv -> {
            RemediationExecution e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(UUID.randomUUID());
            }
            return e;
        });
        when(recommendationRepository.save(any(RemediationRecommendation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(tools.checkApplicationHealth(eq("default"), eq("order-service")))
                .thenReturn(new ApplicationHealth("order-service", 2, 2, 2, true));

        RemediationExecutionResponse response = service().execute(recommendation.getId());

        assertThat(response.status()).isEqualTo("SUCCEEDED");
        assertThat(recommendation.getStatus()).isEqualTo("EXECUTED");
        verify(tools, times(1)).checkApplicationHealth("default", "order-service");
    }
}
