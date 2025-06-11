package com.codirex.eventflow;

import java.util.Objects;

public final class SubscriberInfo {
    private final String methodName;
    private final String eventType; // Store as String (fully qualified name)
    private final ThreadMode threadMode;
    private final int priority;
    private final boolean sticky;
    private final String
            subscriberClassName; // Fully qualified name of the class containing the subscriber
                                 // method

    public SubscriberInfo(
            String subscriberClassName,
            String methodName,
            String eventType,
            ThreadMode threadMode,
            int priority,
            boolean sticky) {
        this.subscriberClassName = subscriberClassName;
        this.methodName = methodName;
        this.eventType = eventType;
        this.threadMode = threadMode;
        this.priority = priority;
        this.sticky = sticky;
    }

    // Add getters for all fields
    public String getSubscriberClassName() {
        return subscriberClassName;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getEventType() {
        return eventType;
    }

    public ThreadMode getThreadMode() {
        return threadMode;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isSticky() {
        return sticky;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SubscriberInfo that = (SubscriberInfo) o;
        return priority == that.priority
                && sticky == that.sticky
                && Objects.equals(methodName, that.methodName)
                && Objects.equals(eventType, that.eventType)
                && threadMode == that.threadMode
                && Objects.equals(subscriberClassName, that.subscriberClassName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                subscriberClassName, methodName, eventType, threadMode, priority, sticky);
    }

    @Override
    public String toString() {
        return "SubscriberInfo{"
                + "subscriberClassName='"
                + subscriberClassName
                + '\''
                + ", methodName='"
                + methodName
                + '\''
                + ", eventType='"
                + eventType
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
