package com.agrosense.frontend.ai;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** The assistant could not answer. The message is written for the end user and the status says why. */
@Getter
public class AiAssistantException extends RuntimeException {

    private final HttpStatus status;

    public AiAssistantException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}