package com.agrosense.frontend.exception;

/** A request that is well formed but not allowed right now; the message is shown to the user. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
