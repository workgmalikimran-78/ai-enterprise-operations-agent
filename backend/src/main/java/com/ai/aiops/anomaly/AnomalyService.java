package com.ai.aiops.anomaly;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ai.aiops.domain.Anomaly;
import com.ai.aiops.domain.Application;
import com.ai.aiops.kafka.AnomalyEventMessage;
import com.ai.aiops.kafka.DomainEventProducer;
import com.ai.aiops.repository.AnomalyRepository;
import com.ai.aiops.repository.ApplicationRepository;

@Service
public class AnomalyService {

    private final AnomalyRepository anomalyRepository;
    private final ApplicationRepository applicationRepository;
    private final DomainEventProducer eventProducer;
    private final IncidentService incidentService;

    public AnomalyService(AnomalyRepository anomalyRepository, ApplicationRepository applicationRepository,
                           DomainEventProducer eventProducer, IncidentService incidentService) {
        this.anomalyRepository = anomalyRepository;
        this.applicationRepository = applicationRepository;
        this.eventProducer = eventProducer;
        this.incidentService = incidentService;
    }

    @Transactional
    public Anomaly createAnomaly(UUID applicationId, String detectionType, String severity, String description,
                                  Map<String, Object> metadata, Instant windowStart, Instant windowEnd) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalStateException("Unknown application: " + applicationId));

        Anomaly anomaly = new Anomaly();
        anomaly.setTenant(application.getTenant());
        anomaly.setApplication(application);
        anomaly.setDetectionType(detectionType);
        anomaly.setStatus("OPEN");
        anomaly.setSeverity(severity);
        anomaly.setDescription(description);
        anomaly.setMetadata(metadata);
        anomaly.setWindowStart(windowStart);
        anomaly.setWindowEnd(windowEnd);

        Anomaly saved = anomalyRepository.save(anomaly);

        eventProducer.publishAnomaly(new AnomalyEventMessage(
                saved.getId(), applicationId, detectionType, severity, description, windowStart, windowEnd));

        incidentService.maybeCreateIncident(saved);

        return saved;
    }

    /**
     * Raises an already-created anomaly's severity when later events in the same detection window
     * push its count past a higher tier, and re-checks incident creation since that may now be the
     * first time this anomaly qualifies (e.g. MEDIUM -> HIGH).
     */
    @Transactional
    public Anomaly escalateSeverity(UUID anomalyId, String newSeverity, String description, long count) {
        Anomaly anomaly = anomalyRepository.findById(anomalyId)
                .orElseThrow(() -> new IllegalStateException("Unknown anomaly: " + anomalyId));

        anomaly.setSeverity(newSeverity);
        anomaly.setDescription(description);
        Map<String, Object> updatedMetadata = new java.util.HashMap<>(anomaly.getMetadata());
        updatedMetadata.put("count", count);
        anomaly.setMetadata(updatedMetadata);
        Anomaly saved = anomalyRepository.save(anomaly);

        eventProducer.publishAnomaly(new AnomalyEventMessage(
                saved.getId(), saved.getApplication().getId(), saved.getDetectionType(), newSeverity, description,
                saved.getWindowStart(), saved.getWindowEnd()));

        incidentService.maybeCreateIncident(saved);

        return saved;
    }
}
