package com.codirex.eventflow.spi;

import com.codirex.eventflow.ThreadMode;

public interface SubscriberInfoProvider {

    Class<?> getSubscriberClass();

    String getSubscriberMethodName();

    Class<?> getEventType();

    ThreadMode getThreadMode();

    int getPriority();

    boolean isSticky();

    SubscriberMethodExecutor getMethodExecutor();
}
