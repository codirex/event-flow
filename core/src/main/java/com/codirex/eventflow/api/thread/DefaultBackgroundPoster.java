package com.codirex.eventflow.api.thread;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class DefaultBackgroundPoster implements BackgroundPoster {

    private final ExecutorService executorService;
    private volatile boolean isActive = true;

    public DefaultBackgroundPoster() {

        this.executorService =
                Executors.newSingleThreadExecutor(
                        r -> {
                            Thread t = new Thread(r, "EventFlow-DefaultBackgroundPoster");
                            t.setDaemon(true);
                            return t;
                        });
    }

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

    public void shutdown() {
        isActive = false;
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
