package com.codirex.eventflow;

import com.codirex.eventflow.api.ErrorContext;
import java.lang.reflect.Method;

/**
 * Default implementation of the {@link ErrorContext} interface.
 * Provides information about the context in which an error occurred during event handling.
 * This class is package-private as it's an internal implementation detail.
 */
class DefaultErrorContextImpl implements ErrorContext {

    private final EventFlow eventFlow;
    private final Object causingEvent;
    private final Object subscriber;
    private final Method subscriberMethod;
    private final String subscriberMethodName;

    DefaultErrorContextImpl(
            EventFlow eventFlow,
            Object causingEvent,
            Object subscriber,
            Method subscriberMethod,
            String subscriberMethodName) {
        this.eventFlow = eventFlow;
        this.causingEvent = causingEvent;
        this.subscriber = subscriber;
        this.subscriberMethod = subscriberMethod;
        this.subscriberMethodName = subscriberMethodName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EventFlow getEventFlow() {
        return eventFlow;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getCausingEvent() {
        return causingEvent;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getSubscriber() {
        return subscriber;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Method getSubscriberMethod() {
        return subscriberMethod;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSubscriberMethodName() {
        return subscriberMethodName;
    }
}
