package com.codirex.eventflow.spi;

import com.codirex.eventflow.EventFlowException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ReflectiveMethodExecutor implements SubscriberMethodExecutor {

    private final Method method;

    public ReflectiveMethodExecutor(Method method) {
        if (method == null) {
            throw new IllegalArgumentException(
                    "Method for ReflectiveMethodExecutor cannot be null.");
        }
        this.method = method;

        try {
            this.method.setAccessible(true);
        } catch (SecurityException e) {

            System.err.println(
                    "EventFlow: Warning - Could not set method accessible for "
                            + method.getName()
                            + " due to SecurityManager: "
                            + e.getMessage());
        }
    }

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

            throw new EventFlowException(
                    "ReflectiveMethodExecutor: Failed to access method "
                            + method.getDeclaringClass().getName()
                            + "#"
                            + method.getName(),
                    e);
        } catch (IllegalArgumentException e) {

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

    public Method getMethod() {
        return method;
    }

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

    @Override
    public String toString() {
        return "ReflectiveMethodExecutor{method="
                + method.getDeclaringClass().getName()
                + "#"
                + method.getName()
                + "}";
    }
}
