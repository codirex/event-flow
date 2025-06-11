package com.codirex.eventflow;

public class EventFlowException extends RuntimeException {

    public EventFlowException(String message) {
        super(message);
    }

    public EventFlowException(String message, Throwable cause) {
        super(message, cause);
    }
}
