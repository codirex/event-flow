package com.codirex.eventflow;

import com.codirex.eventflow.api.ErrorContext;
import java.lang.reflect.Method;

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

    @Override
    public EventFlow getEventFlow() {
        return eventFlow;
    }

    @Override
    public Object getCausingEvent() {
        return causingEvent;
    }

    @Override
    public Object getSubscriber() {
        return subscriber;
    }

    @Override
    public Method getSubscriberMethod() {
        return subscriberMethod;
    }

    @Override
    public String getSubscriberMethodName() {
        return subscriberMethodName;
    }
}
