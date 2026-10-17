package com.agrosense.frontend.ai;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AiAssistantException extends RuntimeException {

    private final HttpStatus status;

    public AiAssistantException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}