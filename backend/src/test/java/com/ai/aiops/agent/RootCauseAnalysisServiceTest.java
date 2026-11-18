package com.ai.aiops.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ai.aiops.api.dto.RcaResponse;
import com.ai.aiops.common.exception.LlmUnavailableException;
import com.ai.aiops.common.exception.ResourceNotFoundException;
import com.ai.aiops.domain.AgentExecution;
import com.ai.aiops.domain.Application;
import com.ai.aiops.domain.Incident;
import com.ai.aiops.domain.RootCauseAnalysis;
import com.ai.aiops.domain.Tenant;
import com.ai.aiops.repository.AgentExecutionRepository;
import com.ai.aiops.repository.IncidentRepository;
import com.ai.aiops.repository.RootCauseAnalysisRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class RootCauseAnalysisServiceTest {

    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private InvestigationAgent investigationAgent;
    @Mock
    private RootCauseAnalysisAgent rcaAgent;
    @Mock
    private RootCauseAnalysisRepository rcaRepository;
    @Mock
    private AgentExecutionRepository agentExecutionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RootCauseAnalysisService service() {
        return new RootCauseAnalysisService(incidentRepository, investigationAgent, rcaAgent, rcaRepository,
                agentExecutionRepository, objectMapper);
    }

    private Incident incident(String status) {
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
        incident.setStatus(status);
        return incident;
    }

    private InvestigationContext emptyContext(UUID incidentId) {
        return new InvestigationContext(incidentId, "t", "HIGH", "order-service",
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    @Test
    void throwsNotFoundForUnknownIncident() {
        UUID id = UUID.randomUUID();
        when(incidentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().investigate(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void investigatePersistsRcaAndTransitionsOpenIncidentToInvestigating() {
        Incident incident = incident("OPEN");
        when(incidentRepository.findById(incident.getId())).thenReturn(Optional.of(incident));
        when(agentExecutionRepository.save(any(AgentExecution.class))).thenAnswer(inv -> inv.getArgument(0));
        when(investigationAgent.gatherContext(incident)).thenReturn(emptyContext(incident.getId()));

        RcaOutput output = new RcaOutput("summary", "pool exhaustion", 1.5 /* out of range on purpose */,
                List.of("fact"), List.of("hypothesis"), List.of("gap"));
        when(rcaAgent.analyze(any())).thenReturn(output);
        when(rcaRepository.save(any(RootCauseAnalysis.class))).thenAnswer(inv -> {
            RootCauseAnalysis saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        RcaResponse response = service().investigate(incident.getId());

        assertThat(response.summary()).isEqualTo("summary");
        assertThat(response.confidence().doubleValue()).isEqualTo(1.0); // clamped from 1.5
        assertThat(incident.getStatus()).isEqualTo("INVESTIGATING");
        verify(incidentRepository).save(incident);
    }

    @Test
    void doesNotRetransitionIncidentAlreadyPastOpen() {
        Incident incident = incident("INVESTIGATING");
        when(incidentRepository.findById(incident.getId())).thenReturn(Optional.of(incident));
        when(agentExecutionRepository.save(any(AgentExecution.class))).thenAnswer(inv -> inv.getArgument(0));
        when(investigationAgent.gatherContext(incident)).thenReturn(emptyContext(incident.getId()));
        when(rcaAgent.analyze(any())).thenReturn(
                new RcaOutput("s", "h", 0.5, List.of(), List.of(), List.of()));
        when(rcaRepository.save(any(RootCauseAnalysis.class))).thenAnswer(inv -> {
            RootCauseAnalysis saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        service().investigate(incident.getId());

        verify(incidentRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void marksAgentExecutionFailedWhenLlmCallFails() {
        Incident incident = incident("OPEN");
        when(incidentRepository.findById(incident.getId())).thenReturn(Optional.of(incident));
        when(agentExecutionRepository.save(any(AgentExecution.class))).thenAnswer(inv -> inv.getArgument(0));
        when(investigationAgent.gatherContext(incident)).thenReturn(emptyContext(incident.getId()));
        when(rcaAgent.analyze(any())).thenThrow(new LlmUnavailableException("no key configured"));

        assertThatThrownBy(() -> service().investigate(incident.getId()))
                .isInstanceOf(LlmUnavailableException.class);

        ArgumentCaptor<AgentExecution> captor = ArgumentCaptor.forClass(AgentExecution.class);
        verify(agentExecutionRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).anySatisfy(exec -> assertThat(exec.getStatus()).isEqualTo("FAILED"));
    }
}
