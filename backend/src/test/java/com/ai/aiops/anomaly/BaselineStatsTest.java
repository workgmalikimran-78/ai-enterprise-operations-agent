package com.ai.aiops.anomaly;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class BaselineStatsTest {

    @Test
    void computesMeanAndStddevFromSamples() {
        BaselineStats stats = BaselineStats.fromSamples(List.of(0.1, 0.1, 0.1, 0.1));

        assertThat(stats.mean()).isEqualTo(0.1, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(stats.sampleCount()).isEqualTo(4);
    }

    @Test
    void returnsZeroStatsForEmptySamples() {
        BaselineStats stats = BaselineStats.fromSamples(List.of());

        assertThat(stats.mean()).isZero();
        assertThat(stats.stddev()).isZero();
        assertThat(stats.sampleCount()).isZero();
    }

    @Test
    void singleSampleHasZeroStddev() {
        BaselineStats stats = BaselineStats.fromSamples(List.of(0.5));

        assertThat(stats.mean()).isEqualTo(0.5);
        assertThat(stats.stddev()).isZero();
        assertThat(stats.sampleCount()).isEqualTo(1);
    }

    @Test
    void zScoreDoesNotDivideByZeroWhenStddevIsZero() {
        BaselineStats stats = BaselineStats.fromSamples(List.of(0.1, 0.1, 0.1));

        double zScore = stats.zScore(0.9);

        assertThat(zScore).isFinite();
        assertThat(zScore).isGreaterThan(0);
    }

    @Test
    void zScoreIsZeroWhenCurrentValueEqualsMean() {
        BaselineStats stats = BaselineStats.fromSamples(List.of(0.1, 0.2, 0.3));

        double zScore = stats.zScore(stats.mean());

        assertThat(zScore).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void zScoreIsPositiveWhenCurrentValueAboveBaseline() {
        BaselineStats stats = BaselineStats.fromSamples(List.of(0.1, 0.15, 0.1, 0.12, 0.11));

        double zScore = stats.zScore(0.9);

        assertThat(zScore).isPositive();
    }
}
