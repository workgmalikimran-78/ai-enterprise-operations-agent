package com.ai.aiops.anomaly;

import java.util.List;

/** Simple moving-average baseline (mean/stddev) used for z-score style deviation checks. */
public record BaselineStats(double mean, double stddev, int sampleCount) {

    private static final double MIN_STDDEV = 1e-4;

    public static BaselineStats fromSamples(List<Double> samples) {
        if (samples.isEmpty()) {
            return new BaselineStats(0.0, 0.0, 0);
        }
        double mean = samples.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        if (samples.size() < 2) {
            return new BaselineStats(mean, 0.0, samples.size());
        }
        double variance = samples.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .sum() / (samples.size() - 1);
        return new BaselineStats(mean, Math.sqrt(variance), samples.size());
    }

    /** Guards against divide-by-zero when the baseline has no observed variance yet. */
    public double zScore(double currentValue) {
        double safeStddev = Math.max(stddev, MIN_STDDEV);
        return (currentValue - mean) / safeStddev;
    }
}
