package com.codirex.eventflow.api;

import com.codirex.eventflow.EventFlow;
import java.lang.reflect.Method;

/**
 * Provides context information to an {@link ErrorHandler} when an exception occurs
 * during event dispatch or subscriber invocation.
 * This context helps in understanding the circumstances of the error.
 */
public interface ErrorContext {

    /**
     * Returns the {@link EventFlow} instance that was handling the event when the error occurred.
     *
     * @return The EventFlow instance.
     */
    EventFlow getEventFlow();

    /**
     * Returns the event object that was being processed when the error occurred.
     *
     * @return The event object.
     */
    Object getCausingEvent();

    /**
     * Returns the subscriber object whose method threw the exception or was about to be called.
     *
     * @return The subscriber object.
     */
    Object getSubscriber();

    /**
     * Returns the {@link Method} of the subscriber that caused the error or was being invoked.
     * This might be null if the error occurred before method resolution (e.g., if using an index
     * and the method executor itself fails).
     *
     * @return The subscriber method, or null if not applicable.
     */
    Method getSubscriberMethod();

    /**
     * Returns the name of the subscriber method that was intended to be invoked or was invoked.
     * This is useful especially if {@link #getSubscriberMethod()} returns null (e.g., when using
     * an index where the method name is known but the Method object might not be readily available
     * without reflection).
     *
     * @return The name of the subscriber method.
     */
    String getSubscriberMethodName();
}
