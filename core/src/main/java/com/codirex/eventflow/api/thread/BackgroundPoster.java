package com.codirex.eventflow.api.thread;

/**
 * Interface for posting tasks to a background thread.
 * This is used by {@link com.codirex.eventflow.EventFlow} for subscribers that specify
 * {@link com.codirex.eventflow.ThreadMode#BACKGROUND}.
 * <p>
 * Implementations typically manage a queue and a single background thread to process tasks sequentially.
 *
 * @see DefaultBackgroundPoster
 */
public interface BackgroundPoster {
    /**
     * Enqueues a {@link Runnable} to be executed in a background thread.
     * The execution order of runnables should typically be FIFO (First-In, First-Out).
     *
     * @param runnable The runnable task to execute.
     */
    void enqueue(Runnable runnable);
}
