package com.ai.aiops.anomaly;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ai.aiops.kafka.KafkaTopics;
import com.ai.aiops.kafka.LogEventMessage;

/**
 * Compares each application's error rate over the most recently-completed window against a
 * rolling baseline (simple moving average), flagging a statistical anomaly when the deviation
 * exceeds a configurable z-score threshold. Runs as a scheduled job rather than per-event, since
 * it needs a completed window's aggregate rate, not a single log line.
 */
@Component
public class StatisticalAnomalyDetector {

    private static final Logger log = LoggerFactory.getLogger(StatisticalAnomalyDetector.class);
    private static final Set<String> ERROR_LEVELS = Set.of("ERROR", "FATAL");

    /** Below this many events in a window, the rate is too noisy to judge - skip evaluation. */
    private static final long MIN_VOLUME_FOR_EVALUATION = 5;
    private static final int MIN_BASELINE_SAMPLES = 3;

    private final AnomalyWindowCounterService counterService;
    private final StringRedisTemplate redisTemplate;
    private final AnomalyService anomalyService;
    private final Duration window;
    private final double zScoreThreshold;
    private final int baselineSampleSize;

    public StatisticalAnomalyDetector(AnomalyWindowCounterService counterService, StringRedisTemplate redisTemplate,
                                       AnomalyService anomalyService,
                                       @Value("${aiops.anomaly.statistical.window-minutes}") long windowMinutes,
                                       @Value("${aiops.anomaly.statistical.zscore-threshold}") double zScoreThreshold,
                                       @Value("${aiops.anomaly.statistical.baseline-sample-size}") int baselineSampleSize) {
        this.counterService = counterService;
        this.redisTemplate = redisTemplate;
        this.anomalyService = anomalyService;
        this.window = Duration.ofMinutes(windowMinutes);
        this.zScoreThreshold = zScoreThreshold;
        this.baselineSampleSize = baselineSampleSize;
    }

    @KafkaListener(topics = KafkaTopics.LOG_EVENTS, groupId = "anomaly-statistical")
    public void onLogEvent(LogEventMessage message) {
        long bucket = counterService.currentWindowBucket(window);
        String appId = message.applicationId().toString();

        counterService.increment(totalKey(appId, bucket), window);
        if (ERROR_LEVELS.contains(message.level())) {
            counterService.increment(errorKey(appId, bucket), window);
        }
        redisTemplate.opsForSet().add(activeSetKey(bucket), appId);
        redisTemplate.expire(activeSetKey(bucket), window);
    }

    @Scheduled(fixedRateString = "#{${aiops.anomaly.statistical.window-minutes} * 60000}")
    public void evaluateCompletedWindow() {
        long currentBucket = counterService.currentWindowBucket(window);
        long previousBucket = currentBucket - window.toSeconds();

        Set<String> activeApplications = redisTemplate.opsForSet().members(activeSetKey(previousBucket));
        if (activeApplications == null || activeApplications.isEmpty()) {
            return;
        }

        for (String appId : activeApplications) {
            evaluateApplication(appId, previousBucket);
        }
    }

    private void evaluateApplication(String appId, long bucket) {
        long total = counterService.getCount(totalKey(appId, bucket));
        long errors = counterService.getCount(errorKey(appId, bucket));
        if (total < MIN_VOLUME_FOR_EVALUATION) {
            return;
        }
        double rate = (double) errors / total;

        String baselineKey = baselineKey(appId);
        List<Double> samples = readBaselineSamples(baselineKey);
        BaselineStats baseline = BaselineStats.fromSamples(samples);

        if (baseline.sampleCount() >= MIN_BASELINE_SAMPLES) {
            double zScore = baseline.zScore(rate);
            if (Math.abs(zScore) >= zScoreThreshold && rate > baseline.mean()) {
                raiseAnomaly(appId, rate, zScore, total, errors, bucket);
            }
        }

        pushBaselineSample(baselineKey, rate);
    }

    private void raiseAnomaly(String appId, double rate, double zScore, long total, long errors, long bucket) {
        String triggerKey = "aiops:anomaly:stat-triggered:%s:%d".formatted(appId, bucket);
        if (!counterService.tryMarkTriggered(triggerKey, window)) {
            return;
        }

        Instant windowEnd = Instant.ofEpochSecond(bucket + window.toSeconds());
        Instant windowStart = Instant.ofEpochSecond(bucket);

        try {
            anomalyService.createAnomaly(
                    java.util.UUID.fromString(appId),
                    "STATISTICAL",
                    severityFor(zScore),
                    "Error rate %.1f%% (%d/%d) deviates %.1f standard deviations from baseline".formatted(
                            rate * 100, errors, total, zScore),
                    Map.of("errorRate", rate, "zScore", zScore, "totalEvents", total, "errorEvents", errors),
                    windowStart,
                    windowEnd);
        } catch (IllegalStateException ex) {
            log.warn("Skipping statistical anomaly for unknown application {}: {}", appId, ex.getMessage());
        }
    }

    private String severityFor(double zScore) {
        double magnitude = Math.abs(zScore);
        if (magnitude >= zScoreThreshold * 2) {
            return "CRITICAL";
        }
        if (magnitude >= zScoreThreshold * 1.5) {
            return "HIGH";
        }
        return "MEDIUM";
    }

    private List<Double> readBaselineSamples(String baselineKey) {
        List<String> raw = redisTemplate.opsForList().range(baselineKey, 0, -1);
        if (raw == null) {
            return List.of();
        }
        return raw.stream().map(Double::parseDouble).toList();
    }

    private void pushBaselineSample(String baselineKey, double rate) {
        redisTemplate.opsForList().leftPush(baselineKey, String.valueOf(rate));
        redisTemplate.opsForList().trim(baselineKey, 0, baselineSampleSize - 1);
    }

    private String totalKey(String appId, long bucket) {
        return "aiops:stat:total:%s:%d".formatted(appId, bucket);
    }

    private String errorKey(String appId, long bucket) {
        return "aiops:stat:error:%s:%d".formatted(appId, bucket);
    }

    private String activeSetKey(long bucket) {
        return "aiops:stat:active:%d".formatted(bucket);
    }

    private String baselineKey(String appId) {
        return "aiops:stat:baseline:%s".formatted(appId);
    }
}
