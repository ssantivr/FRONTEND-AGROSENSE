package com.agrosense.frontend.backend;

/** The backend API did not answer, or answered in a way that says nothing about the request itself. */
public class BackendUnavailableException extends RuntimeException {

    public BackendUnavailableException(String message) {
        super(message);
    }

    public BackendUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
