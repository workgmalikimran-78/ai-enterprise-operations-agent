package com.ai.aiops.anomaly;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ai.aiops.domain.Anomaly;
import com.ai.aiops.kafka.LogEventMessage;

@ExtendWith(MockitoExtension.class)
class RuleBasedAnomalyDetectorTest {

    @Mock
    private AnomalyWindowCounterService counterService;
    @Mock
    private AnomalyService anomalyService;

    private RuleBasedAnomalyDetector detector() {
        return new RuleBasedAnomalyDetector(counterService, anomalyService, 10, 5);
    }

    private LogEventMessage errorMessage(UUID applicationId, String exceptionType) {
        return new LogEventMessage(UUID.randomUUID(), applicationId, UUID.randomUUID(), Instant.now(),
                "order-service", "production", "ERROR", "trace-1", "boom", exceptionType, "boom message");
    }

    @Test
    void ignoresNonErrorLevelEvents() {
        LogEventMessage message = new LogEventMessage(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Instant.now(), "order-service", "production", "INFO", null, "fine", null, null);

        detector().onLogEvent(message);

        verify(counterService, never()).increment(anyString(), any());
    }

    @Test
    void ignoresErrorEventsWithoutExceptionType() {
        LogEventMessage message = new LogEventMessage(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Instant.now(), "order-service", "production", "ERROR", null, "fine", null, null);

        detector().onLogEvent(message);

        verify(counterService, never()).increment(anyString(), any());
    }

    @Test
    void doesNotCreateAnomalyBelowThreshold() {
        UUID applicationId = UUID.randomUUID();
        when(counterService.currentWindowBucket(any())).thenReturn(1000L);
        when(counterService.increment(anyString(), any())).thenReturn(5L);

        detector().onLogEvent(errorMessage(applicationId, "SQLTransientConnectionException"));

        verify(anomalyService, never()).createAnomaly(any(), any(), any(), any(), any(), any(), any());
    }

    private Anomaly anomalyWithId() {
        Anomaly anomaly = new Anomaly();
        anomaly.setId(UUID.randomUUID());
        return anomaly;
    }

    @Test
    void createsAnomalyOnceThresholdCrossedAndDedupsSameWindow() {
        UUID applicationId = UUID.randomUUID();
        when(counterService.currentWindowBucket(any())).thenReturn(1000L);
        when(counterService.increment(anyString(), any())).thenReturn(10L);
        when(counterService.tryMarkTriggered(anyString(), any())).thenReturn(true);
        when(anomalyService.createAnomaly(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(anomalyWithId());

        detector().onLogEvent(errorMessage(applicationId, "SQLTransientConnectionException"));

        verify(anomalyService, times(1)).createAnomaly(
                eq(applicationId), eq("RULE_BASED"), eq("MEDIUM"), anyString(), any(), any(), any());
    }

    @Test
    void doesNotCreateDuplicateAnomalyWhenAlreadyTriggeredForWindow() {
        UUID applicationId = UUID.randomUUID();
        when(counterService.currentWindowBucket(any())).thenReturn(1000L);
        when(counterService.increment(anyString(), any())).thenReturn(11L);
        when(counterService.tryMarkTriggered(anyString(), any())).thenReturn(false);

        detector().onLogEvent(errorMessage(applicationId, "SQLTransientConnectionException"));

        verify(anomalyService, never()).createAnomaly(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void severityEscalatesWithHigherCounts() {
        // Covers a second, independent trigger (e.g. a fresh window) where the very first crossing
        // already happens at a high count - createAnomaly itself can be called with a high-tier
        // severity if `count` jumps straight past threshold*2 on the triggering event.
        UUID applicationId = UUID.randomUUID();
        when(counterService.currentWindowBucket(any())).thenReturn(1000L);
        when(counterService.increment(anyString(), any())).thenReturn(35L);
        when(counterService.tryMarkTriggered(anyString(), any())).thenReturn(true);
        when(anomalyService.createAnomaly(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(anomalyWithId());

        detector().onLogEvent(errorMessage(applicationId, "SQLTransientConnectionException"));

        verify(anomalyService).createAnomaly(
                eq(applicationId), eq("RULE_BASED"), eq("CRITICAL"), anyString(), any(), any(), any());
    }

    @Test
    void escalatesExistingAnomalySeverityWhenLaterCountCrossesHigherTier() {
        UUID applicationId = UUID.randomUUID();
        UUID anomalyId = UUID.randomUUID();
        when(counterService.currentWindowBucket(any())).thenReturn(1000L);
        when(counterService.increment(anyString(), any())).thenReturn(25L);
        when(counterService.tryMarkTriggered(anyString(), any())).thenReturn(false); // already triggered earlier
        when(counterService.recall(contains(":severity:"))).thenReturn("MEDIUM");
        when(counterService.recall(contains(":id:"))).thenReturn(anomalyId.toString());

        detector().onLogEvent(errorMessage(applicationId, "SQLTransientConnectionException"));

        verify(anomalyService).escalateSeverity(eq(anomalyId), eq("HIGH"), anyString(), eq(25L));
        verify(anomalyService, never()).createAnomaly(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void doesNotEscalateWhenCountStillInSameSeverityTier() {
        UUID applicationId = UUID.randomUUID();
        when(counterService.currentWindowBucket(any())).thenReturn(1000L);
        when(counterService.increment(anyString(), any())).thenReturn(12L);
        when(counterService.tryMarkTriggered(anyString(), any())).thenReturn(false);
        when(counterService.recall(contains(":severity:"))).thenReturn("MEDIUM");

        detector().onLogEvent(errorMessage(applicationId, "SQLTransientConnectionException"));

        verify(anomalyService, never()).escalateSeverity(any(), any(), any(), anyLong());
    }
}
