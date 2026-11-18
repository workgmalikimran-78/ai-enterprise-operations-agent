package com.ai.aiops.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ai.aiops.domain.Anomaly;
import com.ai.aiops.domain.Application;
import com.ai.aiops.domain.Incident;
import com.ai.aiops.domain.IncidentAnomaly;
import com.ai.aiops.domain.Tenant;
import com.ai.aiops.repository.IncidentAnomalyRepository;
import com.ai.aiops.repository.IncidentRepository;
import com.ai.aiops.repository.LogEventRepository;
import com.ai.aiops.repository.RunbookRepository;

@ExtendWith(MockitoExtension.class)
class InvestigationAgentTest {

    @Mock
    private IncidentAnomalyRepository incidentAnomalyRepository;
    @Mock
    private LogEventRepository logEventRepository;
    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private RunbookRepository runbookRepository;

    private InvestigationAgent agent() {
        return new InvestigationAgent(incidentAnomalyRepository, logEventRepository, incidentRepository,
                runbookRepository);
    }

    private Incident incident() {
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
        incident.setOpenedAt(Instant.now());
        return incident;
    }

    @Test
    void alwaysDiscloseKnownEvidenceGapsRatherThanInventingData() {
        Incident incident = incident();
        when(incidentAnomalyRepository.findByIncidentId(incident.getId())).thenReturn(List.of());
        when(logEventRepository.findTop20ByApplicationIdAndLevelInAndTimestampGreaterThanEqualOrderByTimestampDesc(
                any(), any(), any())).thenReturn(List.of());
        when(incidentRepository.findByApplicationIdAndStatusInOrderByOpenedAtDesc(any(), any()))
                .thenReturn(List.of());
        when(runbookRepository.findByTenantIdAndActiveTrue(any())).thenReturn(List.of());

        InvestigationContext context = agent().gatherContext(incident);

        assertThat(context.evidenceGaps()).isNotEmpty();
        assertThat(context.evidenceGaps()).anySatisfy(gap -> assertThat(gap).containsIgnoringCase("deployment"));
        assertThat(context.anomalies()).isEmpty();
        assertThat(context.recentErrorLogs()).isEmpty();
    }

    @Test
    void includesLinkedAnomaliesAndExcludesSelfFromHistoricalIncidents() {
        Incident incident = incident();

        Anomaly anomaly = new Anomaly();
        anomaly.setId(UUID.randomUUID());
        anomaly.setDetectionType("RULE_BASED");
        anomaly.setSeverity("HIGH");
        anomaly.setDescription("too many SQLTransientConnectionException");
        anomaly.setWindowStart(Instant.now().minusSeconds(300));
        anomaly.setWindowEnd(Instant.now());
        IncidentAnomaly link = new IncidentAnomaly();
        link.setAnomaly(anomaly);

        when(incidentAnomalyRepository.findByIncidentId(incident.getId())).thenReturn(List.of(link));
        when(logEventRepository.findTop20ByApplicationIdAndLevelInAndTimestampGreaterThanEqualOrderByTimestampDesc(
                any(), any(), any())).thenReturn(List.of());
        when(incidentRepository.findByApplicationIdAndStatusInOrderByOpenedAtDesc(any(), any()))
                .thenReturn(List.of(incident)); // only "historical" incident is itself
        when(runbookRepository.findByTenantIdAndActiveTrue(any())).thenReturn(List.of());

        InvestigationContext context = agent().gatherContext(incident);

        assertThat(context.anomalies()).hasSize(1);
        assertThat(context.anomalies().get(0).description()).contains("SQLTransientConnectionException");
        assertThat(context.historicalIncidents()).isEmpty();
    }
}
