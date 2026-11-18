package com.ai.aiops.execution;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ai.aiops.repository.LogEventRepository;

/**
 * Compares the application's error volume before and after a remediation action to decide
 * whether the incident looks resolved. Never declares success without checking this evidence -
 * if there isn't enough "after" data yet, the caller should wait and verify later rather than
 * guessing.
 */
@Component
public class VerificationAgent {

    private static final List<String> ERROR_LEVELS = List.of("ERROR", "FATAL");
    private static final Duration COMPARISON_WINDOW = Duration.ofMinutes(5);
    /** "After" error count must drop by at least this fraction of "before" to count as improvement. */
    private static final double PARTIAL_IMPROVEMENT_THRESHOLD = 0.5;

    private final LogEventRepository logEventRepository;

    public VerificationAgent(LogEventRepository logEventRepository) {
        this.logEventRepository = logEventRepository;
    }

    public VerificationResult verify(UUID applicationId, Instant executedAt) {
        Instant beforeStart = executedAt.minus(COMPARISON_WINDOW);
        Instant afterEnd = Instant.now();

        long errorsBefore = logEventRepository.countByApplicationIdAndLevelInAndTimestampBetween(
                applicationId, ERROR_LEVELS, beforeStart, executedAt);
        long errorsAfter = logEventRepository.countByApplicationIdAndLevelInAndTimestampBetween(
                applicationId, ERROR_LEVELS, executedAt, afterEnd);

        String status;
        String explanation;
        if (errorsAfter == 0) {
            status = "RESOLVED";
            explanation = "No %s-level log events observed in the window since remediation.".formatted(ERROR_LEVELS);
        } else if (errorsBefore > 0 && errorsAfter <= errorsBefore * (1 - PARTIAL_IMPROVEMENT_THRESHOLD)) {
            status = "PARTIALLY_RESOLVED";
            explanation = "Error volume dropped from %d to %d since remediation, but has not reached zero."
                    .formatted(errorsBefore, errorsAfter);
        } else {
            status = "UNRESOLVED";
            explanation = "Error volume has not meaningfully improved since remediation (%d before, %d after)."
                    .formatted(errorsBefore, errorsAfter);
        }

        return new VerificationResult(status, explanation, errorsBefore, errorsAfter);
    }

    public record VerificationResult(String status, String explanation, long errorsBefore, long errorsAfter) {
    }
}
