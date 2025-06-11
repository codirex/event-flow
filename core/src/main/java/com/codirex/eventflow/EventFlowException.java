package com.codirex.eventflow;

/**
 * A custom {@link RuntimeException} thrown for errors specific to the EventFlow library.
 * This can include configuration issues, problems during event dispatch, or subscriber invocation errors
 * if not handled by a custom {@link com.codirex.eventflow.api.ErrorHandler}.
 */
public class EventFlowException extends RuntimeException {

    /**
     * Constructs a new EventFlowException with the specified detail message.
     *
     * @param message The detail message (which is saved for later retrieval by the {@link #getMessage()} method).
     */
    public EventFlowException(String message) {
        super(message);
    }

    /**
     * Constructs a new EventFlowException with the specified detail message and cause.
     *
     * <p>Note that the detail message associated with {@code cause} is <i>not</i> automatically
     * incorporated in this runtime exception's detail message.
     *
     * @param message The detail message (which is saved for later retrieval by the {@link #getMessage()} method).
     * @param cause The cause (which is saved for later retrieval by the {@link #getCause()} method).
     *              (A {@code null} value is permitted, and indicates that the cause is nonexistent or unknown.)
     */
    public EventFlowException(String message, Throwable cause) {
        super(message, cause);
    }
}
