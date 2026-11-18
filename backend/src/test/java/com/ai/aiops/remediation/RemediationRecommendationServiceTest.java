package com.ai.aiops.remediation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ai.aiops.api.dto.RemediationRecommendationResponse;
import com.ai.aiops.approval.ApprovalService;
import com.ai.aiops.domain.AgentExecution;
import com.ai.aiops.domain.Application;
import com.ai.aiops.domain.Incident;
import com.ai.aiops.domain.RemediationRecommendation;
import com.ai.aiops.domain.RootCauseAnalysis;
import com.ai.aiops.domain.Tenant;
import com.ai.aiops.repository.AgentExecutionRepository;
import com.ai.aiops.repository.RemediationRecommendationRepository;
import com.ai.aiops.repository.RootCauseAnalysisRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class RemediationRecommendationServiceTest {

    @Mock
    private RootCauseAnalysisRepository rcaRepository;
    @Mock
    private RemediationRecommendationRepository recommendationRepository;
    @Mock
    private RemediationRecommendationAgent agent;
    @Mock
    private ApprovalService approvalService;
    @Mock
    private AgentExecutionRepository agentExecutionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RemediationRecommendationService service() {
        return new RemediationRecommendationService(rcaRepository, recommendationRepository, agent, approvalService,
                agentExecutionRepository, objectMapper);
    }

    private RootCauseAnalysis rcaWithIncident() {
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
        incident.setTitle("order-service: too many errors");
        incident.setSeverity("HIGH");

        RootCauseAnalysis rca = new RootCauseAnalysis();
        rca.setId(UUID.randomUUID());
        rca.setIncident(incident);
        rca.setSummary("summary");
        rca.setConfidence(java.math.BigDecimal.valueOf(0.8));
        rca.setRawResponse(Map.of(
                "summary", "summary", "rootCauseHypothesis", "pool exhaustion", "confidence", 0.8,
                "confirmedFacts", java.util.List.of(), "hypotheses", java.util.List.of(),
                "evidenceGaps", java.util.List.of()));
        return rca;
    }

    @Test
    void highRiskActionTriggersApprovalRequestAndStatusTransition() {
        RootCauseAnalysis rca = rcaWithIncident();
        when(rcaRepository.findFirstByIncidentIdOrderByCreatedAtDesc(rca.getIncident().getId()))
                .thenReturn(Optional.of(rca));
        when(agentExecutionRepository.save(any(AgentExecution.class))).thenAnswer(inv -> inv.getArgument(0));
        when(agent.recommend(any(), anyString(), anyString(), anyString())).thenReturn(
                new RemediationOutput("RESTART_DEPLOYMENT", "restart it", "errors stop", "customers can't checkout",
                        "manual rollback if restart fails"));
        when(recommendationRepository.save(any(RemediationRecommendation.class))).thenAnswer(inv -> {
            RemediationRecommendation r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(UUID.randomUUID());
            }
            return r;
        });

        RemediationRecommendationResponse response = service().recommend(rca.getIncident().getId());

        assertThat(response.actionType()).isEqualTo("RESTART_DEPLOYMENT");
        assertThat(response.riskLevel()).isEqualTo("HIGH");
        assertThat(response.requiredApprovalLevel()).isEqualTo("HIGH");
        verify(approvalService).createApprovalRequest(any());
    }

    @Test
    void readOnlyActionDoesNotTriggerApproval() {
        RootCauseAnalysis rca = rcaWithIncident();
        when(rcaRepository.findFirstByIncidentIdOrderByCreatedAtDesc(rca.getIncident().getId()))
                .thenReturn(Optional.of(rca));
        when(agentExecutionRepository.save(any(AgentExecution.class))).thenAnswer(inv -> inv.getArgument(0));
        when(agent.recommend(any(), anyString(), anyString(), anyString())).thenReturn(
                new RemediationOutput("CHECK_APPLICATION_HEALTH", "check health", "confirm status", "none yet",
                        "n/a"));
        when(recommendationRepository.save(any(RemediationRecommendation.class))).thenAnswer(inv -> {
            RemediationRecommendation r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(UUID.randomUUID());
            }
            return r;
        });

        RemediationRecommendationResponse response = service().recommend(rca.getIncident().getId());

        assertThat(response.requiredApprovalLevel()).isEqualTo("NONE");
        verify(approvalService, never()).createApprovalRequest(any());
    }

    @Test
    void unrecognizedActionTypeFallsBackToInvestigateFurther() {
        RootCauseAnalysis rca = rcaWithIncident();
        when(rcaRepository.findFirstByIncidentIdOrderByCreatedAtDesc(rca.getIncident().getId()))
                .thenReturn(Optional.of(rca));
        when(agentExecutionRepository.save(any(AgentExecution.class))).thenAnswer(inv -> inv.getArgument(0));
        when(agent.recommend(any(), anyString(), anyString(), anyString())).thenReturn(
                new RemediationOutput("DELETE_PRODUCTION_DATABASE", "not allowed", "n/a", "n/a", "n/a"));
        when(recommendationRepository.save(any(RemediationRecommendation.class))).thenAnswer(inv -> {
            RemediationRecommendation r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(UUID.randomUUID());
            }
            return r;
        });

        RemediationRecommendationResponse response = service().recommend(rca.getIncident().getId());

        assertThat(response.actionType()).isEqualTo("INVESTIGATE_FURTHER");
        assertThat(response.riskLevel()).isEqualTo("LOW");
    }
}
