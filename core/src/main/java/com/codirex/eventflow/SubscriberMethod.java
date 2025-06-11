package com.codirex.eventflow;

import com.codirex.eventflow.spi.SubscriberInfoProvider;
import com.codirex.eventflow.spi.SubscriberMethodExecutor;
import com.codirex.eventflow.spi.ReflectiveMethodExecutor;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Represents a subscriber method found via reflection.
 * This class encapsulates a {@link Method} along with its subscription details like
 * event type, {@link ThreadMode}, priority, and sticky behavior.
 * It implements {@link SubscriberInfoProvider} to offer a common interface for
 * accessing subscriber details, whether they come from reflection or an index.
 */
public class SubscriberMethod implements SubscriberInfoProvider {

    final Method method;
    final Class<?> eventType;
    final ThreadMode threadMode;
    final int priority;
    final boolean sticky;
    final String methodString;

    /**
     * Constructs a new SubscriberMethod.
     *
     * @param method The subscriber {@link Method}. Must not be null.
     * @param eventType The type of event this method subscribes to. Must not be null.
     * @param threadMode The {@link ThreadMode} for event delivery. Must not be null.
     * @param priority The priority of this subscriber method.
     * @param sticky True if this method should receive sticky events, false otherwise.
     * @throws IllegalArgumentException if method, eventType, or threadMode is null.
     */
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

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<?> getSubscriberClass() {
        return method.getDeclaringClass();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<?> getEventType() {
        return eventType;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ThreadMode getThreadMode() {
        return threadMode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getPriority() {
        return priority;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSticky() {
        return sticky;
    }

    /**
     * {@inheritDoc}
     * This implementation returns a {@link ReflectiveMethodExecutor} for this method.
     */
    @Override
    public SubscriberMethodExecutor getMethodExecutor() {

        return new ReflectiveMethodExecutor(this.method);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSubscriberMethodName() {
        return this.method.getName();
    }

    /**
     * Returns the underlying {@link Method} object for this subscriber.
     * @return The Java {@link Method}.
     */
    public Method getMethod() {
        return method;
    }

    /**
     * Returns a string representation of the method, including declaring class, method name, and event type.
     * Useful for identification and debugging.
     * @return A string identifying the method.
     */
    public String getMethodString() {
        return methodString;
    }

    /**
     * Compares this SubscriberMethod to the specified object. The result is true if and only if
     * the argument is not null and is a SubscriberMethod object that has the same method string
     * (which is derived from the method's declaring class, name, and event type).
     *
     * @param o The object to compare this SubscriberMethod against.
     * @return True if the given object represents a SubscriberMethod equivalent to this one, false otherwise.
     */
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

    /**
     * Returns a string representation of the SubscriberMethod object, including its method string,
     * thread mode, priority, and sticky status.
     *
     * @return A string representation of the object.
     */
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
