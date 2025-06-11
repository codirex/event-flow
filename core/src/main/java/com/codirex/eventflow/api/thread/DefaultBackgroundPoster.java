package com.codirex.eventflow.api.thread;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Default implementation of {@link BackgroundPoster}.
 * It uses a single-threaded {@link ExecutorService} to process tasks sequentially in a dedicated background thread.
 * This ensures that all background events are handled in the order they are posted.
 */
public class DefaultBackgroundPoster implements BackgroundPoster {

    private final ExecutorService executorService;
    private volatile boolean isActive = true;

    /**
     * Constructs a new DefaultBackgroundPoster.
     * Initializes a single-threaded executor service with a daemon thread named "EventFlow-DefaultBackgroundPoster".
     */
    public DefaultBackgroundPoster() {

        this.executorService =
                Executors.newSingleThreadExecutor(
                        r -> {
                            Thread t = new Thread(r, "EventFlow-DefaultBackgroundPoster");
                            t.setDaemon(true); // Ensure it doesn't prevent JVM shutdown
                            return t;
                        });
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation submits the runnable to a single-threaded {@link ExecutorService}.
     * If the poster has been shut down or the runnable is null, the task will be ignored and a message
     * will be printed to System.err. If the executor rejects the task, an error is also logged.
     *
     * @param runnable The runnable task to execute.
     */
    @Override
    public void enqueue(Runnable runnable) {
        if (!isActive) {

            System.err.println(
                    "EventFlow: DefaultBackgroundPoster is not active. Cannot enqueue task.");
            return;
        }
        if (runnable == null) {

            System.err.println(
                    "EventFlow: DefaultBackgroundPoster received null runnable to enqueue. Ignoring.");
            return;
        }
        try {
            executorService.submit(runnable);
        } catch (RejectedExecutionException e) {

            System.err.println(
                    "EventFlow: Task rejected by DefaultBackgroundPoster's executor during enqueue: "
                            + e.getMessage());
        }
    }

    /**
     * Shuts down the background poster.
     * Sets the poster to inactive and initiates an orderly shutdown of its internal {@link ExecutorService}.
     * Previously submitted tasks are executed before termination, but no new tasks will be accepted.
     * This method should be called when the EventFlow instance using this poster is shut down,
     * especially if this poster was created and managed by EventFlow itself.
     */
    public void shutdown() {
        isActive = false;
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
