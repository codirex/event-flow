package com.codirex.eventflow.api;

import com.codirex.eventflow.EventFlow;
import java.lang.reflect.Method;

public interface ErrorContext {

    EventFlow getEventFlow();

    Object getCausingEvent();

    Object getSubscriber();

    Method getSubscriberMethod();

    String getSubscriberMethodName();
}
