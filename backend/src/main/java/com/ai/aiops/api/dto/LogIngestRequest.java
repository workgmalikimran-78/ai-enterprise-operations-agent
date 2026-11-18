package com.ai.aiops.api.dto;

import java.time.Instant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record LogIngestRequest(
        Instant timestamp,
        @NotBlank String service,
        @NotBlank String environment,
        @NotBlank String level,
        String traceId,
        @NotBlank String message,
        @Valid ExceptionInfo exception
) {
    public record ExceptionInfo(String type, String message, String stackTrace) {
    }
}
