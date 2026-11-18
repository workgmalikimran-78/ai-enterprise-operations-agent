package com.ai.aiops.dashboard;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ai.aiops.api.dto.DashboardMetricsResponse;
import com.ai.aiops.api.dto.DashboardOverviewResponse;
import com.ai.aiops.repository.AgentExecutionRepository;
import com.ai.aiops.repository.AnomalyRepository;
import com.ai.aiops.repository.IncidentRepository;

@Service
public class DashboardService {

    private final IncidentRepository incidentRepository;
    private final AnomalyRepository anomalyRepository;
    private final AgentExecutionRepository agentExecutionRepository;

    public DashboardService(IncidentRepository incidentRepository, AnomalyRepository anomalyRepository,
                             AgentExecutionRepository agentExecutionRepository) {
        this.incidentRepository = incidentRepository;
        this.anomalyRepository = anomalyRepository;
        this.agentExecutionRepository = agentExecutionRepository;
    }

    @Transactional(readOnly = true)
    public DashboardOverviewResponse overview() {
        long total = incidentRepository.count();
        long critical = incidentRepository.countBySeverityAndStatusNot("CRITICAL", "CLOSED");
        long activeAnomalies = anomalyRepository.countByStatus("OPEN");
        long resolved = incidentRepository.countByStatus("RESOLVED") + incidentRepository.countByStatus("CLOSED");
        Double mttd = anomalyRepository.averageDetectionSeconds();
        Double mttr = incidentRepository.averageResolutionSeconds();
        long investigations = agentExecutionRepository.countByAgentType("ROOT_CAUSE_ANALYSIS");
        long investigationFailures = agentExecutionRepository.countByAgentTypeAndStatus("ROOT_CAUSE_ANALYSIS",
                "FAILED");

        return new DashboardOverviewResponse(total, critical, activeAnomalies, resolved, mttd, mttr, investigations,
                investigationFailures);
    }

    @Transactional(readOnly = true)
    public DashboardMetricsResponse metrics() {
        return new DashboardMetricsResponse(
                toMap(incidentRepository.countGroupedBySeverity()),
                toMap(incidentRepository.countGroupedByStatus()),
                toMap(anomalyRepository.countGroupedByDetectionType()),
                toMap(agentExecutionRepository.countGroupedByAgentType())
        );
    }

    private Map<String, Long> toMap(Iterable<Object[]> rows) {
        Map<String, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put(String.valueOf(row[0]), (Long) row[1]);
        }
        return map;
    }
}
