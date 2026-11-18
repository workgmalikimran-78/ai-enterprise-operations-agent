package com.ai.aiops.anomaly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ai.aiops.domain.Anomaly;
import com.ai.aiops.domain.Application;
import com.ai.aiops.domain.Tenant;
import com.ai.aiops.kafka.AnomalyEventMessage;
import com.ai.aiops.kafka.DomainEventProducer;
import com.ai.aiops.repository.AnomalyRepository;
import com.ai.aiops.repository.ApplicationRepository;

@ExtendWith(MockitoExtension.class)
class AnomalyServiceTest {

    @Mock
    private AnomalyRepository anomalyRepository;
    @Mock
    private ApplicationRepository applicationRepository;
    @Mock
    private DomainEventProducer eventProducer;
    @Mock
    private IncidentService incidentService;

    @Test
    void persistsPublishesAndTriggersIncidentEvaluation() {
        UUID applicationId = UUID.randomUUID();
        Application application = new Application();
        application.setId(applicationId);
        application.setTenant(new Tenant());

        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(anomalyRepository.save(any(Anomaly.class))).thenAnswer(invocation -> {
            Anomaly anomaly = invocation.getArgument(0);
            anomaly.setId(UUID.randomUUID());
            return anomaly;
        });

        AnomalyService service = new AnomalyService(anomalyRepository, applicationRepository, eventProducer,
                incidentService);

        Instant start = Instant.now().minusSeconds(300);
        Instant end = Instant.now();
        Anomaly saved = service.createAnomaly(applicationId, "RULE_BASED", "HIGH", "too many errors",
                Map.of("count", 15), start, end);

        assertThat(saved.getStatus()).isEqualTo("OPEN");
        verify(anomalyRepository).save(any(Anomaly.class));
        verify(eventProducer).publishAnomaly(any(AnomalyEventMessage.class));
        verify(incidentService).maybeCreateIncident(saved);
    }
}
