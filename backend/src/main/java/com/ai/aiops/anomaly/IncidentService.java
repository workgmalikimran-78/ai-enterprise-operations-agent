package com.ai.aiops.anomaly;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ai.aiops.domain.Anomaly;
import com.ai.aiops.domain.Incident;
import com.ai.aiops.domain.IncidentAnomaly;
import com.ai.aiops.domain.IncidentAnomalyId;
import com.ai.aiops.kafka.DomainEventProducer;
import com.ai.aiops.kafka.IncidentEventMessage;
import com.ai.aiops.repository.IncidentAnomalyRepository;
import com.ai.aiops.repository.IncidentRepository;

/**
 * Decides whether an anomaly warrants opening a new incident, or should instead be linked to an
 * incident that's already open for the same application - this correlation step is what stops a
 * sustained outage from spawning a new incident per anomaly.
 */
@Service
public class IncidentService {

    /** Only anomalies at or above this severity open/extend an incident. */
    private static final List<String> SEVERITY_ORDER = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final String INCIDENT_THRESHOLD_SEVERITY = "HIGH";

    private final IncidentRepository incidentRepository;
    private final IncidentAnomalyRepository incidentAnomalyRepository;
    private final DomainEventProducer eventProducer;

    public IncidentService(IncidentRepository incidentRepository, IncidentAnomalyRepository incidentAnomalyRepository,
                            DomainEventProducer eventProducer) {
        this.incidentRepository = incidentRepository;
        this.incidentAnomalyRepository = incidentAnomalyRepository;
        this.eventProducer = eventProducer;
    }

    @Transactional
    public void maybeCreateIncident(Anomaly anomaly) {
        if (SEVERITY_ORDER.indexOf(anomaly.getSeverity()) < SEVERITY_ORDER.indexOf(INCIDENT_THRESHOLD_SEVERITY)) {
            return;
        }

        List<Incident> openIncidents = incidentRepository.findByApplicationIdAndStatusOrderByOpenedAtDesc(
                anomaly.getApplication().getId(), "OPEN");

        if (!openIncidents.isEmpty()) {
            Incident existing = openIncidents.get(0);
            if (!linkAnomalyToIncident(existing, anomaly)) {
                return; // already linked - e.g. a severity escalation re-running this check
            }
            eventProducer.publishIncident(new IncidentEventMessage(
                    "INCIDENT_ANOMALY_LINKED", existing.getId(), anomaly.getApplication().getId(),
                    anomaly.getId(), anomaly.getSeverity()));
            return;
        }

        Incident incident = new Incident();
        incident.setTenant(anomaly.getTenant());
        incident.setApplication(anomaly.getApplication());
        incident.setTitle(anomaly.getApplication().getName() + ": " + anomaly.getDescription());
        incident.setStatus("OPEN");
        incident.setSeverity(anomaly.getSeverity());
        incident.setOpenedAt(Instant.now());
        Incident saved = incidentRepository.save(incident);

        linkAnomalyToIncident(saved, anomaly);

        eventProducer.publishIncident(new IncidentEventMessage(
                "INCIDENT_OPENED", saved.getId(), anomaly.getApplication().getId(), anomaly.getId(),
                anomaly.getSeverity()));
    }

    /** Returns false (no-op) if this anomaly is already linked to this incident - keeps repeated
     *  calls for the same anomaly (e.g. a later severity escalation) safe to re-run. */
    private boolean linkAnomalyToIncident(Incident incident, Anomaly anomaly) {
        IncidentAnomalyId id = new IncidentAnomalyId(incident.getId(), anomaly.getId());
        if (incidentAnomalyRepository.existsById(id)) {
            return false;
        }
        incidentAnomalyRepository.save(new IncidentAnomaly(incident, anomaly));
        return true;
    }
}
