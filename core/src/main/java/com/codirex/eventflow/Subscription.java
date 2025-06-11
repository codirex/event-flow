package com.codirex.eventflow;

import com.codirex.eventflow.spi.SubscriberInfoProvider;
import java.util.Objects;

public class Subscription {
    final Object subscriber;
    final SubscriberInfoProvider infoProvider;

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

    public Object getSubscriber() {
        return subscriber;
    }

    public SubscriberInfoProvider getInfoProvider() {
        return infoProvider;
    }

    public Class<?> getEventType() {
        return infoProvider.getEventType();
    }

    public ThreadMode getThreadMode() {
        return infoProvider.getThreadMode();
    }

    public int getPriority() {
        return infoProvider.getPriority();
    }

    public boolean isSticky() {
        return infoProvider.isSticky();
    }

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
