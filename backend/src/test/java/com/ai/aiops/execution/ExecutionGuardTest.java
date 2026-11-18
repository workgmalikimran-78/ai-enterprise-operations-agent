package com.ai.aiops.execution;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.ai.aiops.common.exception.ValidationException;

class ExecutionGuardTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);

    private ExecutionGuard guard(boolean enabled, int maxPerHour) {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        return new ExecutionGuard(enabled, List.of("default", "staging"), maxPerHour, redisTemplate);
    }

    @Test
    void killSwitchBlocksAllTargetsWhenDisabled() {
        ExecutionGuard guard = guard(false, 100);

        assertThatThrownBy(() -> guard.validateTarget("default", "order-service"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("kill switch");
    }

    @Test
    void disabledAtRuntimeEvenIfInitiallyEnabled() {
        ExecutionGuard guard = guard(true, 100);
        assertThatCode(() -> guard.validateTarget("default", "order-service")).doesNotThrowAnyException();

        guard.disableExecution();

        assertThatThrownBy(() -> guard.validateTarget("default", "order-service"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsNamespaceNotInAllowlist() {
        ExecutionGuard guard = guard(true, 100);

        assertThatThrownBy(() -> guard.validateTarget("kube-system", "coredns"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("not in the allowed-namespaces");
    }

    @Test
    void rejectsInvalidKubernetesResourceNames() {
        ExecutionGuard guard = guard(true, 100);

        assertThatThrownBy(() -> guard.validateTarget("default", "Invalid_Name!"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid Kubernetes resource name");
    }

    @Test
    void rateLimitBlocksAfterThreshold() {
        ExecutionGuard guard = guard(true, 2);
        when(valueOps.increment(anyString())).thenReturn(1L, 2L, 3L);

        assertThatCode(() -> guard.checkRateLimit("default", "order-service")).doesNotThrowAnyException();
        assertThatCode(() -> guard.checkRateLimit("default", "order-service")).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.checkRateLimit("default", "order-service"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Rate limit exceeded");
    }
}
