package com.ai.aiops.anomaly;

import java.time.Duration;
import java.time.Instant;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed fixed-window counters and dedup flags, used so detectors never need to run a
 * COUNT(*) against Postgres/OpenSearch on every single log event.
 */
@Component
public class AnomalyWindowCounterService {

    private final StringRedisTemplate redisTemplate;

    public AnomalyWindowCounterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** Truncates "now" down to the start of the current fixed window, used as a cache-key suffix. */
    public long currentWindowBucket(Duration window) {
        long windowSeconds = window.toSeconds();
        return (Instant.now().getEpochSecond() / windowSeconds) * windowSeconds;
    }

    /** Increments the counter at {@code key}, setting its TTL to {@code window} the first time it's created. */
    public long increment(String key, Duration window) {
        Long value = redisTemplate.opsForValue().increment(key);
        if (value != null && value == 1L) {
            redisTemplate.expire(key, window);
        }
        return value != null ? value : 0L;
    }

    public long getCount(String key) {
        String value = redisTemplate.opsForValue().get(key);
        return value != null ? Long.parseLong(value) : 0L;
    }

    /** Returns true only the first time this flag is set for the given window - used to dedup anomaly creation. */
    public boolean tryMarkTriggered(String key, Duration window) {
        Boolean set = redisTemplate.opsForValue().setIfAbsent(key, "1", window);
        return Boolean.TRUE.equals(set);
    }

    /** Stores an arbitrary value against the current window, used to remember e.g. an anomaly's id. */
    public void remember(String key, String value, Duration window) {
        redisTemplate.opsForValue().set(key, value, window);
    }

    public String recall(String key) {
        return redisTemplate.opsForValue().get(key);
    }
}
