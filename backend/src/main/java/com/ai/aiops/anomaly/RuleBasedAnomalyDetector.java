package com.ai.aiops.anomaly;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.ai.aiops.domain.Anomaly;
import com.ai.aiops.kafka.KafkaTopics;
import com.ai.aiops.kafka.LogEventMessage;

/**
 * Counts occurrences of each (application, exceptionType) pair within a fixed window and raises
 * an anomaly once a configurable threshold is crossed. Only reads numeric/categorical log fields
 * (level, exceptionType) - never the free-text message/stack trace - and never calls any LLM.
 *
 * <p>The anomaly fires the instant the threshold is first crossed (so alerting stays real-time
 * rather than waiting for the window to close), which means its initial severity always lands on
 * the lowest tier - count is, by definition, exactly {@code threshold} at that moment. Later
 * events in the same window keep incrementing the same counter and escalate the already-created
 * anomaly's severity (and re-check incident creation) if the running count crosses a higher tier,
 * rather than leaving the HIGH/CRITICAL tiers unreachable.
 */
@Component
public class RuleBasedAnomalyDetector {

    private static final Set<String> ERROR_LEVELS = Set.of("ERROR", "FATAL");
    private static final List<String> SEVERITY_ORDER = List.of("MEDIUM", "HIGH", "CRITICAL");

    private final AnomalyWindowCounterService counterService;
    private final AnomalyService anomalyService;
    private final int threshold;
    private final Duration window;

    public RuleBasedAnomalyDetector(AnomalyWindowCounterService counterService, AnomalyService anomalyService,
                                     @Value("${aiops.anomaly.rule.threshold}") int threshold,
                                     @Value("${aiops.anomaly.rule.window-minutes}") long windowMinutes) {
        this.counterService = counterService;
        this.anomalyService = anomalyService;
        this.threshold = threshold;
        this.window = Duration.ofMinutes(windowMinutes);
    }

    @KafkaListener(topics = KafkaTopics.LOG_EVENTS, groupId = "anomaly-detector")
    public void onLogEvent(LogEventMessage message) {
        if (!ERROR_LEVELS.contains(message.level()) || message.exceptionType() == null) {
            return;
        }

        long bucket = counterService.currentWindowBucket(window);
        String countKey = "aiops:anomaly:count:%s:%s:%d".formatted(
                message.applicationId(), message.exceptionType(), bucket);
        long count = counterService.increment(countKey, window);

        if (count < threshold) {
            return;
        }

        String triggerKey = "aiops:anomaly:triggered:%s:%s:%d".formatted(
                message.applicationId(), message.exceptionType(), bucket);
        String anomalyIdKey = "aiops:anomaly:id:%s:%s:%d".formatted(
                message.applicationId(), message.exceptionType(), bucket);
        String severityKey = "aiops:anomaly:severity:%s:%s:%d".formatted(
                message.applicationId(), message.exceptionType(), bucket);

        if (counterService.tryMarkTriggered(triggerKey, window)) {
            Instant windowEnd = Instant.now();
            Instant windowStart = windowEnd.minus(window);
            String severity = severityFor(count);
            Anomaly created = anomalyService.createAnomaly(
                    message.applicationId(),
                    "RULE_BASED",
                    severity,
                    describe(message, count),
                    Map.of("exceptionType", message.exceptionType(), "count", count, "threshold", threshold),
                    windowStart,
                    windowEnd);
            counterService.remember(anomalyIdKey, created.getId().toString(), window);
            counterService.remember(severityKey, severity, window);
            return;
        }

        // Already raised an anomaly for this window - escalate its severity if the running count
        // has now crossed a higher tier than when it was first created, rather than leaving it
        // frozen at whatever count happened to trigger the very first crossing of `threshold`.
        String newSeverity = severityFor(count);
        String currentSeverity = counterService.recall(severityKey);
        if (currentSeverity == null || SEVERITY_ORDER.indexOf(newSeverity) <= SEVERITY_ORDER.indexOf(currentSeverity)) {
            return;
        }
        String anomalyId = counterService.recall(anomalyIdKey);
        if (anomalyId == null) {
            return;
        }
        anomalyService.escalateSeverity(UUID.fromString(anomalyId), newSeverity, describe(message, count), count);
        counterService.remember(severityKey, newSeverity, window);
    }

    private String describe(LogEventMessage message, long count) {
        return "%s occurred %d times in %d minutes on %s".formatted(
                message.exceptionType(), count, window.toMinutes(), message.service());
    }

    private String severityFor(long count) {
        if (count >= threshold * 3L) {
            return "CRITICAL";
        }
        if (count >= threshold * 2L) {
            return "HIGH";
        }
        return "MEDIUM";
    }
}
