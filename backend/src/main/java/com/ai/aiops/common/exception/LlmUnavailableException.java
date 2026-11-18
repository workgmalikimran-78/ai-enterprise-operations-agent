package com.ai.aiops.common.exception;

import org.springframework.http.HttpStatus;

/** Thrown when an agent needs to call the LLM but no provider is configured, or the call fails. */
public class LlmUnavailableException extends AiopsException {

    public LlmUnavailableException(String message) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE);
    }

    public LlmUnavailableException(String message, Throwable cause) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE);
        initCause(cause);
    }
}
