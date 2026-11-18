package com.ai.aiops.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ai.aiops.api.dto.DashboardMetricsResponse;
import com.ai.aiops.api.dto.DashboardOverviewResponse;
import com.ai.aiops.repository.AgentExecutionRepository;
import com.ai.aiops.repository.AnomalyRepository;
import com.ai.aiops.repository.IncidentRepository;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private AnomalyRepository anomalyRepository;
    @Mock
    private AgentExecutionRepository agentExecutionRepository;

    private DashboardService service() {
        return new DashboardService(incidentRepository, anomalyRepository, agentExecutionRepository);
    }

    @Test
    void overviewAggregatesCountsFromAllRepositories() {
        when(incidentRepository.count()).thenReturn(42L);
        when(incidentRepository.countBySeverityAndStatusNot("CRITICAL", "CLOSED")).thenReturn(3L);
        when(anomalyRepository.countByStatus("OPEN")).thenReturn(7L);
        when(incidentRepository.countByStatus("RESOLVED")).thenReturn(10L);
        when(incidentRepository.countByStatus("CLOSED")).thenReturn(5L);
        when(anomalyRepository.averageDetectionSeconds()).thenReturn(120.0);
        when(incidentRepository.averageResolutionSeconds()).thenReturn(3600.0);
        when(agentExecutionRepository.countByAgentType("ROOT_CAUSE_ANALYSIS")).thenReturn(20L);
        when(agentExecutionRepository.countByAgentTypeAndStatus("ROOT_CAUSE_ANALYSIS", "FAILED")).thenReturn(2L);

        DashboardOverviewResponse overview = service().overview();

        assertThat(overview.totalIncidents()).isEqualTo(42L);
        assertThat(overview.criticalIncidents()).isEqualTo(3L);
        assertThat(overview.activeAnomalies()).isEqualTo(7L);
        assertThat(overview.resolvedIncidents()).isEqualTo(15L);
        assertThat(overview.mttdSeconds()).isEqualTo(120.0);
        assertThat(overview.mttrSeconds()).isEqualTo(3600.0);
        assertThat(overview.aiInvestigations()).isEqualTo(20L);
        assertThat(overview.aiInvestigationFailures()).isEqualTo(2L);
    }

    @Test
    void metricsConvertsGroupedRowsIntoMaps() {
        when(incidentRepository.countGroupedBySeverity()).thenReturn(
                List.<Object[]>of(new Object[]{"HIGH", 5L}, new Object[]{"LOW", 2L}));
        when(incidentRepository.countGroupedByStatus()).thenReturn(List.<Object[]>of(new Object[]{"OPEN", 3L}));
        when(anomalyRepository.countGroupedByDetectionType()).thenReturn(
                List.<Object[]>of(new Object[]{"RULE_BASED", 10L}));
        when(agentExecutionRepository.countGroupedByAgentType()).thenReturn(
                List.<Object[]>of(new Object[]{"INVESTIGATION", 8L}));

        DashboardMetricsResponse metrics = service().metrics();

        assertThat(metrics.incidentsBySeverity()).containsEntry("HIGH", 5L).containsEntry("LOW", 2L);
        assertThat(metrics.incidentsByStatus()).containsEntry("OPEN", 3L);
        assertThat(metrics.anomaliesByDetectionType()).containsEntry("RULE_BASED", 10L);
        assertThat(metrics.agentExecutionsByType()).containsEntry("INVESTIGATION", 8L);
    }
}
