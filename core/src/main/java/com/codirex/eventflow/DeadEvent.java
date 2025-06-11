package com.codirex.eventflow;

/**
 * Represents an event that was posted but had no subscribers.
 * This event is posted by {@link EventFlow} itself when {@link EventFlow.Builder#sendNoSubscriberEvent(boolean)}
 * is enabled (which it is by default) and an event is posted for which no subscribers are found.
 * It wraps the original event.
 */
public final class DeadEvent {

    private final Object originalEvent;

    /**
     * Constructs a new DeadEvent.
     *
     * @param originalEvent The event that was posted but had no subscribers. Must not be null.
     * @throws IllegalArgumentException if originalEvent is null.
     */
    public DeadEvent(Object originalEvent) {
        if (originalEvent == null) {

            throw new IllegalArgumentException("Original event for DeadEvent must not be null.");
        }
        this.originalEvent = originalEvent;
    }

    /**
     * Returns the original event that was posted but had no subscribers.
     *
     * @return The original event.
     */
    public Object getOriginalEvent() {
        return originalEvent;
    }

    /**
     * Returns a string representation of the DeadEvent, including the string representation of the original event.
     *
     * @return A string representation of this DeadEvent.
     */
    @Override
    public String toString() {
        return "DeadEvent{"
                + "originalEvent="
                + (originalEvent != null ? originalEvent.toString() : "null")
                + '}';
    }
}
