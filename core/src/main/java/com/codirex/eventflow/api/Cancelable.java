package com.codirex.eventflow.api;

/**
 * Base class for events that can be canceled.
 * If an event that extends this class has its {@link #isCanceled()} flag set to true,
 * {@link com.codirex.eventflow.EventFlow} may stop dispatching it to further subscribers,
 * depending on its internal logic (typically, subscribers with lower priority or later in the
 * same priority list might not receive a canceled event).
 * <p>
 * Subscribers can check this flag and potentially set it to true to prevent further processing.
 * <p>
 * Note: This class is abstract to encourage subclassing for specific event types rather than
 * direct instantiation if it were concrete. However, it provides a concrete implementation
 * of the cancellation mechanism.
 */
public abstract class Cancelable {

    private boolean isCanceled;

    /**
     * Checks if this event has been canceled.
     *
     * @return True if the event is canceled, false otherwise.
     */
    public boolean isCanceled() {
        return this.isCanceled;
    }

    /**
     * Sets the canceled state of this event.
     *
     * @param isCanceled True to cancel the event, false to allow it to proceed (if previously canceled).
     */
    public void setIsCanceled(boolean isCanceled) {
        this.isCanceled = isCanceled;
    }

}
