package com.ai.aiops.common.exception;

import org.springframework.http.HttpStatus;

/** Base type for all domain exceptions that should be translated into a structured API error. */
public class AiopsException extends RuntimeException {

    private final HttpStatus status;

    public AiopsException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
