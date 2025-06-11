package com.codirex.eventflow;

import com.codirex.eventflow.spi.SubscriberInfoProvider;
import com.codirex.eventflow.spi.SubscriberMethodExecutor;
import com.codirex.eventflow.spi.ReflectiveMethodExecutor;
import java.lang.reflect.Method;
import java.util.Objects;

public class SubscriberMethod implements SubscriberInfoProvider {

    final Method method;
    final Class<?> eventType;
    final ThreadMode threadMode;
    final int priority;
    final boolean sticky;
    final String methodString;

    public SubscriberMethod(
            Method method,
            Class<?> eventType,
            ThreadMode threadMode,
            int priority,
            boolean sticky) {
        if (method == null) throw new IllegalArgumentException("Method cannot be null.");
        if (eventType == null) throw new IllegalArgumentException("EventType cannot be null.");
        if (threadMode == null) throw new IllegalArgumentException("ThreadMode cannot be null.");

        this.method = method;
        this.eventType = eventType;
        this.threadMode = threadMode;
        this.priority = priority;
        this.sticky = sticky;

        this.methodString =
                method.getDeclaringClass().getName()
                        + "#"
                        + method.getName()
                        + "("
                        + eventType.getName()
                        + ")";
    }

    @Override
    public Class<?> getSubscriberClass() {
        return method.getDeclaringClass();
    }

    @Override
    public Class<?> getEventType() {
        return eventType;
    }

    @Override
    public ThreadMode getThreadMode() {
        return threadMode;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public boolean isSticky() {
        return sticky;
    }

    @Override
    public SubscriberMethodExecutor getMethodExecutor() {

        return new ReflectiveMethodExecutor(this.method);
    }

    @Override
    public String getSubscriberMethodName() {
        return this.method.getName();
    }

    public Method getMethod() {
        return method;
    }

    public String getMethodString() {
        return methodString;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SubscriberMethod that = (SubscriberMethod) o;
        return methodString.equals(that.methodString);
    }

    @Override
    public int hashCode() {
        return methodString.hashCode();
    }

    @Override
    public String toString() {
        return "SubscriberMethod{"
                + "methodString='"
                + methodString
                + '\''
                + ", threadMode="
                + threadMode
                + ", priority="
                + priority
                + ", sticky="
                + sticky
                + '}';
    }
}
