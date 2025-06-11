package com.codirex.eventflow;

import com.codirex.eventflow.spi.SubscriberInfoProvider;
import java.util.Objects;

/**
 * Represents an active subscription of a subscriber object to a specific event type.
 * It pairs a subscriber instance with a {@link SubscriberInfoProvider} which provides
 * details about the subscribed method (like event type, thread mode, priority, etc.).
 */
public class Subscription {
    final Object subscriber;
    final SubscriberInfoProvider infoProvider;

    /**
     * Constructs a new Subscription.
     *
     * @param subscriber The subscriber object instance. Must not be null.
     * @param infoProvider The provider of information about the subscriber method. Must not be null.
     * @throws IllegalArgumentException if subscriber or infoProvider is null.
     */
    public Subscription(Object subscriber, SubscriberInfoProvider infoProvider) {
        if (subscriber == null) {
            throw new IllegalArgumentException("Subscriber cannot be null.");
        }
        if (infoProvider == null) {
            throw new IllegalArgumentException("SubscriberInfoProvider cannot be null.");
        }
        this.subscriber = subscriber;
        this.infoProvider = infoProvider;
    }

    /**
     * Returns the subscriber object instance.
     * @return The subscriber object.
     */
    public Object getSubscriber() {
        return subscriber;
    }

    /**
     * Returns the {@link SubscriberInfoProvider} associated with this subscription.
     * This provider gives access to details like event type, thread mode, etc.
     * @return The subscriber info provider.
     */
    public SubscriberInfoProvider getInfoProvider() {
        return infoProvider;
    }

    /**
     * Convenience method to get the event type for this subscription directly from the info provider.
     * @return The class of the event type.
     */
    public Class<?> getEventType() {
        return infoProvider.getEventType();
    }

    /**
     * Convenience method to get the {@link ThreadMode} for this subscription directly from the info provider.
     * @return The thread mode.
     */
    public ThreadMode getThreadMode() {
        return infoProvider.getThreadMode();
    }

    /**
     * Convenience method to get the priority for this subscription directly from the info provider.
     * @return The priority value.
     */
    public int getPriority() {
        return infoProvider.getPriority();
    }

    /**
     * Convenience method to check if this subscription is sticky, directly from the info provider.
     * @return True if the subscription is for sticky events, false otherwise.
     */
    public boolean isSticky() {
        return infoProvider.isSticky();
    }

    /**
     * Compares this Subscription to the specified object. The result is true if and only if
     * the argument is not null and is a Subscription object that has the same subscriber instance
     * and the same {@link SubscriberInfoProvider}.
     *
     * @param o The object to compare this Subscription against.
     * @return True if the given object represents a Subscription equivalent to this one, false otherwise.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Subscription that = (Subscription) o;

        return Objects.equals(subscriber, that.subscriber)
                && Objects.equals(infoProvider, that.infoProvider);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subscriber, infoProvider);
    }

    /**
     * Returns a string representation of the Subscription object, including details about the subscriber,
     * event type, thread mode, priority, and sticky status.
     *
     * @return A string representation of the object.
     */
    @Override
    public String toString() {
        return "Subscription{subscriber="
                + subscriber.getClass().getName()
                + ", eventType="
                + getEventType().getName()
                + ", threadMode="
                + getThreadMode()
                + ", priority="
                + getPriority()
                + ", sticky="
                + isSticky()
                + "}";
    }
}
