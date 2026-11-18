package com.ai.aiops.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ai.aiops.repository.LogEventRepository;

@ExtendWith(MockitoExtension.class)
class VerificationAgentTest {

    @Mock
    private LogEventRepository logEventRepository;

    private VerificationAgent agent() {
        return new VerificationAgent(logEventRepository);
    }

    private static final List<String> ERROR_LEVELS = List.of("ERROR", "FATAL");

    @Test
    void noErrorsAfterMeansResolved() {
        UUID appId = UUID.randomUUID();
        Instant executedAt = Instant.now().minusSeconds(600);
        when(logEventRepository.countByApplicationIdAndLevelInAndTimestampBetween(
                eq(appId), eq(ERROR_LEVELS), any(), eq(executedAt))).thenReturn(10L);
        when(logEventRepository.countByApplicationIdAndLevelInAndTimestampBetween(
                eq(appId), eq(ERROR_LEVELS), eq(executedAt), any())).thenReturn(0L);

        var result = agent().verify(appId, executedAt);

        assertThat(result.status()).isEqualTo("RESOLVED");
    }

    @Test
    void bigDropButNonZeroMeansPartiallyResolved() {
        UUID appId = UUID.randomUUID();
        Instant executedAt = Instant.now().minusSeconds(600);
        when(logEventRepository.countByApplicationIdAndLevelInAndTimestampBetween(
                eq(appId), eq(ERROR_LEVELS), any(), eq(executedAt))).thenReturn(20L);
        when(logEventRepository.countByApplicationIdAndLevelInAndTimestampBetween(
                eq(appId), eq(ERROR_LEVELS), eq(executedAt), any())).thenReturn(5L);

        var result = agent().verify(appId, executedAt);

        assertThat(result.status()).isEqualTo("PARTIALLY_RESOLVED");
    }

    @Test
    void noImprovementMeansUnresolved() {
        UUID appId = UUID.randomUUID();
        Instant executedAt = Instant.now().minusSeconds(600);
        when(logEventRepository.countByApplicationIdAndLevelInAndTimestampBetween(
                eq(appId), eq(ERROR_LEVELS), any(), eq(executedAt))).thenReturn(10L);
        when(logEventRepository.countByApplicationIdAndLevelInAndTimestampBetween(
                eq(appId), eq(ERROR_LEVELS), eq(executedAt), any())).thenReturn(9L);

        var result = agent().verify(appId, executedAt);

        assertThat(result.status()).isEqualTo("UNRESOLVED");
    }
}
