package com.ai.aiops.agent;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ai.aiops.agent.InvestigationContext.AnomalySummary;
import com.ai.aiops.agent.InvestigationContext.HistoricalIncidentSummary;
import com.ai.aiops.agent.InvestigationContext.LogSummary;
import com.ai.aiops.agent.InvestigationContext.RunbookSummary;
import com.ai.aiops.domain.Anomaly;
import com.ai.aiops.domain.Incident;
import com.ai.aiops.domain.IncidentAnomaly;
import com.ai.aiops.domain.LogEvent;
import com.ai.aiops.domain.Runbook;
import com.ai.aiops.repository.IncidentAnomalyRepository;
import com.ai.aiops.repository.IncidentRepository;
import com.ai.aiops.repository.LogEventRepository;
import com.ai.aiops.repository.RunbookRepository;

/**
 * Gathers verifiable operational context for an incident: linked anomalies, recent error logs,
 * correlated trace IDs, similar historical incidents, and matching runbooks. Does not call any
 * LLM - that's the RCA agent's job, once this evidence has been assembled.
 */
@Component
public class InvestigationAgent {

    private static final List<String> ERROR_LEVELS = List.of("ERROR", "FATAL");
    private static final List<String> CLOSED_STATUSES = List.of("RESOLVED", "CLOSED");
    private static final int HISTORICAL_INCIDENT_LIMIT = 3;

    private final IncidentAnomalyRepository incidentAnomalyRepository;
    private final LogEventRepository logEventRepository;
    private final IncidentRepository incidentRepository;
    private final RunbookRepository runbookRepository;

    public InvestigationAgent(IncidentAnomalyRepository incidentAnomalyRepository,
                               LogEventRepository logEventRepository, IncidentRepository incidentRepository,
                               RunbookRepository runbookRepository) {
        this.incidentAnomalyRepository = incidentAnomalyRepository;
        this.logEventRepository = logEventRepository;
        this.incidentRepository = incidentRepository;
        this.runbookRepository = runbookRepository;
    }

    @Transactional(readOnly = true)
    public InvestigationContext gatherContext(Incident incident) {
        List<Anomaly> anomalies = incidentAnomalyRepository.findByIncidentId(incident.getId()).stream()
                .map(IncidentAnomaly::getAnomaly)
                .toList();

        Instant earliestWindowStart = anomalies.stream()
                .map(Anomaly::getWindowStart)
                .min(Comparator.naturalOrder())
                .orElse(incident.getOpenedAt().minus(15, ChronoUnit.MINUTES));

        List<LogEvent> recentLogs = logEventRepository
                .findTop20ByApplicationIdAndLevelInAndTimestampGreaterThanEqualOrderByTimestampDesc(
                        incident.getApplication().getId(), ERROR_LEVELS, earliestWindowStart);

        List<String> traceIds = recentLogs.stream()
                .map(LogEvent::getTraceId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();

        List<Incident> historical = incidentRepository
                .findByApplicationIdAndStatusInOrderByOpenedAtDesc(incident.getApplication().getId(), CLOSED_STATUSES)
                .stream()
                .filter(i -> !i.getId().equals(incident.getId()))
                .limit(HISTORICAL_INCIDENT_LIMIT)
                .toList();

        Set<String> anomalyKeywords = anomalies.stream()
                .map(a -> a.getDescription() == null ? "" : a.getDescription().toLowerCase())
                .collect(java.util.stream.Collectors.toSet());

        List<Runbook> matchingRunbooks = runbookRepository
                .findByTenantIdAndActiveTrue(incident.getTenant().getId()).stream()
                .filter(rb -> rb.getTriggerPattern() != null
                        && anomalyKeywords.stream().anyMatch(desc -> desc.contains(rb.getTriggerPattern().toLowerCase())))
                .toList();

        List<String> evidenceGaps = new ArrayList<>();
        evidenceGaps.add("No deployment event history is tracked by this system yet, "
                + "so recent deployments could not be checked as a contributing factor.");
        evidenceGaps.add("No infrastructure/database metrics (CPU, memory, connection pool "
                + "utilization) are collected by this system yet.");

        return new InvestigationContext(
                incident.getId(),
                incident.getTitle(),
                incident.getSeverity(),
                incident.getApplication().getName(),
                anomalies.stream().map(this::toAnomalySummary).toList(),
                recentLogs.stream().map(this::toLogSummary).toList(),
                traceIds,
                historical.stream().map(this::toHistoricalSummary).toList(),
                matchingRunbooks.stream().map(this::toRunbookSummary).toList(),
                evidenceGaps
        );
    }

    private AnomalySummary toAnomalySummary(Anomaly a) {
        return new AnomalySummary(a.getDetectionType(), a.getSeverity(), a.getDescription(),
                a.getWindowStart(), a.getWindowEnd(), a.getMetadata());
    }

    private LogSummary toLogSummary(LogEvent e) {
        return new LogSummary(e.getTimestamp(), e.getLevel(), e.getTraceId(), e.getMessage(),
                e.getExceptionType(), e.getExceptionMessage());
    }

    private HistoricalIncidentSummary toHistoricalSummary(Incident i) {
        return new HistoricalIncidentSummary(i.getTitle(), i.getSeverity(), i.getStatus(),
                i.getOpenedAt(), i.getResolvedAt());
    }

    private RunbookSummary toRunbookSummary(Runbook rb) {
        return new RunbookSummary(rb.getName(), rb.getTriggerPattern(), rb.getSteps());
    }
}
