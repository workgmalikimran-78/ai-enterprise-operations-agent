package com.ai.aiops.execution;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.ai.aiops.common.exception.ValidationException;

/**
 * Centralizes the safety checks every mutating Kubernetes tool must pass before touching a real
 * cluster: a runtime-flippable kill switch, a namespace allowlist (never touch arbitrary
 * namespaces), resource-name validation, and a per-target rate limit. This is deliberately
 * separate from business logic so every execution tool goes through the same gate.
 */
@Component
public class ExecutionGuard {

    private static final java.util.regex.Pattern K8S_NAME_PATTERN =
            java.util.regex.Pattern.compile("^[a-z0-9]([-a-z0-9]*[a-z0-9])?$");

    private final AtomicBoolean executionEnabled;
    private final List<String> allowedNamespaces;
    private final StringRedisTemplate redisTemplate;
    private final int maxExecutionsPerHour;

    public ExecutionGuard(@Value("${aiops.execution.enabled:true}") boolean initiallyEnabled,
                           @Value("${aiops.execution.allowed-namespaces:default}") List<String> allowedNamespaces,
                           @Value("${aiops.execution.max-per-hour:5}") int maxExecutionsPerHour,
                           StringRedisTemplate redisTemplate) {
        this.executionEnabled = new AtomicBoolean(initiallyEnabled);
        this.allowedNamespaces = allowedNamespaces;
        this.maxExecutionsPerHour = maxExecutionsPerHour;
        this.redisTemplate = redisTemplate;
    }

    /** Emergency stop: immediately blocks every mutating tool call, cluster-wide, without a redeploy. */
    public void disableExecution() {
        executionEnabled.set(false);
    }

    public void enableExecution() {
        executionEnabled.set(true);
    }

    public boolean isExecutionEnabled() {
        return executionEnabled.get();
    }

    /** Throws if this (namespace, resourceName) target may not be operated on right now. */
    public void validateTarget(String namespace, String resourceName) {
        if (!executionEnabled.get()) {
            throw new ValidationException("Operational execution is disabled (emergency kill switch engaged)");
        }
        if (!allowedNamespaces.contains(namespace)) {
            throw new ValidationException("Namespace '" + namespace + "' is not in the allowed-namespaces list");
        }
        if (!K8S_NAME_PATTERN.matcher(resourceName).matches()) {
            throw new ValidationException("Invalid Kubernetes resource name: " + resourceName);
        }
    }

    /** Throws if the given target has already hit its mutating-action rate limit for the current hour. */
    public void checkRateLimit(String namespace, String resourceName) {
        long hourBucket = Instant.now().getEpochSecond() / 3600;
        String key = "aiops:execution:ratelimit:%s:%s:%d".formatted(namespace, resourceName, hourBucket);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofHours(1));
        }
        if (count != null && count > maxExecutionsPerHour) {
            throw new ValidationException("Rate limit exceeded for " + namespace + "/" + resourceName
                    + " (" + maxExecutionsPerHour + " mutating actions per hour)");
        }
    }
}
