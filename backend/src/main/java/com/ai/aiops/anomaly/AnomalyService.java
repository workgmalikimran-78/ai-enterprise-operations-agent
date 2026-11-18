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
}
