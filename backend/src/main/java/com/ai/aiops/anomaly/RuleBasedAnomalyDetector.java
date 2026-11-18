package com.ai.aiops.anomaly;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.ai.aiops.kafka.KafkaTopics;
import com.ai.aiops.kafka.LogEventMessage;

/**
 * Counts occurrences of each (application, exceptionType) pair within a fixed window and raises
 * an anomaly once a configurable threshold is crossed. Only reads numeric/categorical log fields
 * (level, exceptionType) - never the free-text message/stack trace - and never calls any LLM.
 */
@Component
public class RuleBasedAnomalyDetector {

    private static final Set<String> ERROR_LEVELS = Set.of("ERROR", "FATAL");

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
        if (!counterService.tryMarkTriggered(triggerKey, window)) {
            return; // already raised an anomaly for this window
        }

        Instant windowEnd = Instant.now();
        Instant windowStart = windowEnd.minus(window);
        anomalyService.createAnomaly(
                message.applicationId(),
                "RULE_BASED",
                severityFor(count),
                "%s occurred %d times in %d minutes on %s".formatted(
                        message.exceptionType(), count, window.toMinutes(), message.service()),
                Map.of("exceptionType", message.exceptionType(), "count", count, "threshold", threshold),
                windowStart,
                windowEnd);
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
