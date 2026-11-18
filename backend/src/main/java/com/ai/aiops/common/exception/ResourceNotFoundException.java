package com.ai.aiops.common.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends AiopsException {

    public ResourceNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
