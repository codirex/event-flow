package com.codirex.eventflow.spi;

import com.codirex.eventflow.EventFlowException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * A {@link SubscriberMethodExecutor} that invokes subscriber methods using Java reflection.
 * This executor is used when EventFlow relies on reflection to find and call subscriber methods
 * (i.e., when an {@link com.codirex.eventflow.api.EventFlowIndex} is not used or does not
 * provide a pre-compiled executor).
 * <p>
 * It handles making the method accessible and invoking it with the correct event argument.
 */
public class ReflectiveMethodExecutor implements SubscriberMethodExecutor {

    private final Method method;

    /**
     * Constructs a new ReflectiveMethodExecutor for the given {@link Method}.
     * The method's accessibility will be set to true if possible.
     *
     * @param method The subscriber method to be executed. Must not be null.
     * @throws IllegalArgumentException if the method is null.
     */
    public ReflectiveMethodExecutor(Method method) {
        if (method == null) {
            throw new IllegalArgumentException(
                    "Method for ReflectiveMethodExecutor cannot be null.");
        }
        this.method = method;

        // Attempt to make the method accessible.
        // This is often necessary for methods in non-public classes or non-public methods.
        try {
            this.method.setAccessible(true);
        } catch (SecurityException e) {
            // Log a warning if a SecurityManager prevents making the method accessible.
            // The invocation might still succeed if the method is already public.
            System.err.println(
                    "EventFlow: Warning - Could not set method accessible for "
                            + method.getName()
                            + " due to SecurityManager: "
                            + e.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     * This implementation invokes the underlying {@link Method} on the {@code subscriberTarget}
     * with the given {@code event} as the argument.
     *
     * @throws EventFlowException if the {@code subscriberTarget} is null, if there's an
     *         {@link IllegalAccessException} during invocation (despite attempting to set accessible),
     *         or if there's an {@link IllegalArgumentException} due to type mismatch.
     * @throws InvocationTargetException if the underlying subscriber method itself throws an exception.
     */
    @Override
    public void invoke(Object subscriberTarget, Object event) throws InvocationTargetException {
        if (subscriberTarget == null) {
            throw new EventFlowException(
                    "Subscriber target cannot be null for reflective invocation of method: "
                            + method.getName());
        }

        try {
            method.invoke(subscriberTarget, event);
        } catch (IllegalAccessException e) {
            // This might happen if setAccessible(true) failed or was insufficient.
            throw new EventFlowException(
                    "ReflectiveMethodExecutor: Failed to access method "
                            + method.getDeclaringClass().getName()
                            + "#"
                            + method.getName(),
                    e);
        } catch (IllegalArgumentException e) {
            // This typically indicates a mismatch between the event type and the method's parameter type.
            String expectedParamType =
                    (method.getParameterTypes().length > 0)
                            ? method.getParameterTypes()[0].getName()
                            : "N/A";
            String actualEventType = (event != null) ? event.getClass().getName() : "null";
            throw new EventFlowException(
                    "ReflectiveMethodExecutor: Argument type mismatch for method "
                            + method.getDeclaringClass().getName()
                            + "#"
                            + method.getName()
                            + ". Expected: "
                            + expectedParamType
                            + ", Got: "
                            + actualEventType,
                    e);
        }
    }

    /**
     * Returns the underlying {@link Method} that this executor invokes.
     *
     * @return The method.
     */
    public Method getMethod() {
        return method;
    }

    /**
     * Compares this ReflectiveMethodExecutor to the specified object. The result is true if and only if
     * the argument is not null and is a ReflectiveMethodExecutor object that encapsulates the same {@link Method}.
     *
     * @param o The object to compare this ReflectiveMethodExecutor against.
     * @return True if the given object is equivalent to this one, false otherwise.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReflectiveMethodExecutor that = (ReflectiveMethodExecutor) o;
        return method.equals(that.method);
    }

    @Override
    public int hashCode() {
        return method.hashCode();
    }

    /**
     * Returns a string representation of the ReflectiveMethodExecutor, including the fully qualified name
     * of the method it executes.
     *
     * @return A string representation of the object.
     */
    @Override
    public String toString() {
        return "ReflectiveMethodExecutor{method="
                + method.getDeclaringClass().getName()
                + "#"
                + method.getName()
                + "}";
    }
}
