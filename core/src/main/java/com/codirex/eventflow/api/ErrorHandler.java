package com.codirex.eventflow.api;

public interface ErrorHandler {
    void handleError(Throwable exception, ErrorContext context);
}
