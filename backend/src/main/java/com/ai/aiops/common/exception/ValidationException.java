package com.ai.aiops.common.exception;

import org.springframework.http.HttpStatus;

public class ValidationException extends AiopsException {

    public ValidationException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
