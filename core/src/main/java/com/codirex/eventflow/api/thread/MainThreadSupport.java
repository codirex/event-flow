package com.codirex.eventflow.api.thread;

/**
 * Interface for providing main thread execution support, primarily for environments like Android.
 * This allows {@link com.codirex.eventflow.EventFlow} to dispatch events to subscribers
 * on the main UI thread when {@link com.codirex.eventflow.ThreadMode#MAIN} is specified.
 * <p>
 * An implementation of this interface needs to be provided to {@link com.codirex.eventflow.EventFlow.Builder#mainThreadSupport(MainThreadSupport)}
 * if {@link com.codirex.eventflow.ThreadMode#MAIN} is to be used.
 */
public interface MainThreadSupport {

    /**
     * Checks if the current thread is the main application thread (e.g., Android UI thread).
     *
     * @return True if the current thread is the main thread, false otherwise.
     */
    boolean isMainThread();

    /**
     * Posts a {@link Runnable} to be executed on the main application thread.
     * If the current thread is already the main thread, the runnable may be executed immediately (synchronously)
     * or asynchronously, depending on the implementation. If the current thread is not the main thread,
     * the runnable should be queued for execution on the main thread.
     *
     * @param runnable The runnable task to execute on the main thread.
     */
    void postToMainThread(Runnable runnable);
}
