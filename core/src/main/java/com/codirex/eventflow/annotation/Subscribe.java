package com.codirex.eventflow.annotation;

import com.codirex.eventflow.ThreadMode;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a subscriber to an event.
 * The method must be public and have exactly one parameter, which is the event type.
 * <p>
 * Example:
 * <pre>{@code
 * @Subscribe(threadMode = ThreadMode.MAIN, priority = 1)
 * public void onUserLoggedInEvent(UserLoggedInEvent event) {
 *     // Handle the event on the main thread with priority 1
 * }
 * }</pre>
 *
 * @see com.codirex.eventflow.EventFlow#register(Object)
 * @see com.codirex.eventflow.ThreadMode
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Subscribe {
    /**
     * Specifies the {@link ThreadMode} in which the subscriber method will be called.
     * Default is {@link ThreadMode#POSTING}, meaning the subscriber will be called in the same
     * thread that posted the event.
     *
     * @return The desired thread mode for event delivery.
     */
    ThreadMode threadMode() default ThreadMode.POSTING;

    /**
     * If true, this subscriber will receive the last known sticky event of its subscribed type
     * immediately upon registration. If there is no sticky event of that type, it will behave
     * like a normal subscriber.
     * Default is false.
     *
     * @return True if the subscriber should receive sticky events, false otherwise.
     * @see com.codirex.eventflow.EventFlow#postSticky(Object)
     */
    boolean sticky() default false;

    /**
     * Defines the priority of this subscriber. Subscribers with higher priority values will
     * receive events before subscribers with lower priority values.
     * The order of invocation for subscribers with the same priority is undefined.
     * Default is 0.
     *
     * @return The priority of the subscriber method.
     */
    int priority() default 0;
}
