package com.codirex.eventflow;

public final class DeadEvent {

    private final Object originalEvent;

    public DeadEvent(Object originalEvent) {
        if (originalEvent == null) {

            throw new IllegalArgumentException("Original event for DeadEvent must not be null.");
        }
        this.originalEvent = originalEvent;
    }

    public Object getOriginalEvent() {
        return originalEvent;
    }

    @Override
    public String toString() {
        return "DeadEvent{"
                + "originalEvent="
                + (originalEvent != null ? originalEvent.toString() : "null")
                + '}';
    }
}
