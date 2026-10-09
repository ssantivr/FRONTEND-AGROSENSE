package com.agrosense.frontend.exception;

/** The requested record does not exist or does not belong to the signed-in user. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
