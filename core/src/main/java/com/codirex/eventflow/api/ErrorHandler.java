package com.codirex.eventflow.api;

/**
 * Interface for handling exceptions that occur during event dispatch or subscriber invocation
 * within an {@link com.codirex.eventflow.EventFlow} instance.
 * Implementations of this interface can be registered with {@link com.codirex.eventflow.EventFlow.Builder#errorHandler(ErrorHandler)}
 * to provide custom error handling logic, such as logging or specific recovery actions.
 */
public interface ErrorHandler {
    /**
     * Handles an exception that occurred within EventFlow.
     *
     * @param exception The exception that was thrown.
     * @param context The {@link ErrorContext} providing details about the circumstances of the error,
     *                such as the causing event, subscriber, and EventFlow instance.
     */
    void handleError(Throwable exception, ErrorContext context);
}
