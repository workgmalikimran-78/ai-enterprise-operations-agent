package com.ai.aiops.api.dto;

import java.time.Instant;
import java.util.List;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String correlationId,
        List<String> fieldErrors
) {
    public static ApiError of(int status, String error, String message, String path, String correlationId) {
        return new ApiError(Instant.now(), status, error, message, path, correlationId, List.of());
    }

    public static ApiError of(int status, String error, String message, String path, String correlationId,
                               List<String> fieldErrors) {
        return new ApiError(Instant.now(), status, error, message, path, correlationId, fieldErrors);
    }
}
