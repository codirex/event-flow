package com.codirex.eventflow;

/**
 * Defines the thread on which a subscriber method will be called.
 * EventFlow manages thread transitions based on the specified ThreadMode.
 */
public enum ThreadMode {
    /**
     * Subscriber will be called directly in the same thread that is posting the event.
     * This is the default ThreadMode if not specified.
     * Event delivery is synchronous. Be cautious with long-running operations in this mode
     * as they can block the posting thread.
     */
    POSTING,

    /**
     * Subscriber will be called in Android's main UI thread.
     * If the posting thread is already the main thread, the subscriber will be called directly (synchronously).
     * If the posting thread is a background thread, events will be queued and delivered sequentially on the main thread.
     * This mode requires {@link com.codirex.eventflow.api.thread.MainThreadSupport} to be configured in {@link EventFlow.Builder}.
     */
    MAIN,

    /**
     * Subscriber will be called in a background thread.
     * If the posting thread is not the main thread, the subscriber will be called directly in that posting thread.
     * If the posting thread is the main thread, EventFlow will use a single background thread to deliver all events
     * sequentially. Subscribers should avoid long-running operations in this mode to prevent blocking this background thread.
     * This mode typically uses a {@link com.codirex.eventflow.api.thread.BackgroundPoster}.
     */
    BACKGROUND,

    /**
     * Subscriber will be called in a separate thread than the posting thread.
     * EventFlow manages a thread pool (usually a cached thread pool) to handle these events.
     * This mode is suitable for long-running operations that should not block the posting thread or the main UI thread.
     * Event delivery is asynchronous.
     * This mode requires an {@link java.util.concurrent.ExecutorService} to be configured in {@link EventFlow.Builder}
     * or EventFlow will use a default one.
     */
    ASYNC
}
