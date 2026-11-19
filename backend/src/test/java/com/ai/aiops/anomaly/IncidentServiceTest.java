package com.ai.aiops.anomaly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ai.aiops.domain.Anomaly;
import com.ai.aiops.domain.Application;
import com.ai.aiops.domain.Incident;
import com.ai.aiops.domain.Tenant;
import com.ai.aiops.kafka.DomainEventProducer;
import com.ai.aiops.kafka.IncidentEventMessage;
import com.ai.aiops.repository.IncidentAnomalyRepository;
import com.ai.aiops.repository.IncidentRepository;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private IncidentAnomalyRepository incidentAnomalyRepository;
    @Mock
    private DomainEventProducer eventProducer;

    private IncidentService service() {
        return new IncidentService(incidentRepository, incidentAnomalyRepository, eventProducer);
    }

    private Anomaly anomaly(String severity) {
        Application application = new Application();
        application.setId(UUID.randomUUID());
        application.setTenant(new Tenant());
        application.setName("order-service");

        Anomaly anomaly = new Anomaly();
        anomaly.setId(UUID.randomUUID());
        anomaly.setApplication(application);
        anomaly.setTenant(application.getTenant());
        anomaly.setSeverity(severity);
        anomaly.setDescription("too many errors");
        return anomaly;
    }

    @ParameterizedTest
    @ValueSource(strings = {"LOW", "MEDIUM"})
    void lowSeverityAnomaliesDoNotOpenAnIncident(String severity) {
        service().maybeCreateIncident(anomaly(severity));

        verify(incidentRepository, never()).save(any());
        verify(incidentAnomalyRepository, never()).save(any());
        verify(eventProducer, never()).publishIncident(any());
    }

    @Test
    void highSeverityAnomalyOpensNewIncidentWhenNoneIsOpen() {
        Anomaly anomaly = anomaly("HIGH");
        when(incidentRepository.findByApplicationIdAndStatusOrderByOpenedAtDesc(
                anomaly.getApplication().getId(), "OPEN")).thenReturn(List.of());
        when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> {
            Incident incident = invocation.getArgument(0);
            incident.setId(UUID.randomUUID());
            return incident;
        });

        service().maybeCreateIncident(anomaly);

        verify(incidentRepository).save(any(Incident.class));
        verify(incidentAnomalyRepository).save(any());
        verify(eventProducer).publishIncident(argThatTypeIs("INCIDENT_OPENED"));
    }

    @Test
    void criticalSeverityAnomalyLinksToExistingOpenIncidentInsteadOfDuplicating() {
        Anomaly anomaly = anomaly("CRITICAL");
        Incident existing = new Incident();
        existing.setId(UUID.randomUUID());
        when(incidentRepository.findByApplicationIdAndStatusOrderByOpenedAtDesc(
                anomaly.getApplication().getId(), "OPEN")).thenReturn(List.of(existing));

        service().maybeCreateIncident(anomaly);

        verify(incidentRepository, never()).save(any());
        verify(incidentAnomalyRepository).save(any());
        verify(eventProducer).publishIncident(argThatTypeIs("INCIDENT_ANOMALY_LINKED"));
    }

    @Test
    void reEvaluatingAnAlreadyLinkedAnomalySkipsDuplicateLinkAndEvent() {
        // Mirrors a severity escalation re-running maybeCreateIncident for an anomaly that was
        // already linked on an earlier call - must not violate the incident_anomalies composite PK.
        Anomaly anomaly = anomaly("CRITICAL");
        Incident existing = new Incident();
        existing.setId(UUID.randomUUID());
        when(incidentRepository.findByApplicationIdAndStatusOrderByOpenedAtDesc(
                anomaly.getApplication().getId(), "OPEN")).thenReturn(List.of(existing));
        when(incidentAnomalyRepository.existsById(any())).thenReturn(true);

        service().maybeCreateIncident(anomaly);

        verify(incidentAnomalyRepository, never()).save(any());
        verify(eventProducer, never()).publishIncident(any());
    }

    private IncidentEventMessage argThatTypeIs(String type) {
        return org.mockito.ArgumentMatchers.argThat(msg -> msg.type().equals(type));
    }
}
