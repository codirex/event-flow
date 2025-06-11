package com.codirex.eventflow.linux;

import com.codirex.eventflow.api.thread.MainThreadSupport;
import javafx.application.Platform;

public class JavaFXMainThreadSupport implements MainThreadSupport {
    public JavaFXMainThreadSupport() {}

    @Override
    public boolean isMainThread() {
        try {
            return Platform.isFxApplicationThread();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    @Override
    public void postToMainThread(Runnable runnable) {
        if (runnable == null) {
            System.err.println(
                    "EventBus (JavaFX): Null runnable provided to postToMainThread. Ignoring.");
            return;
        }
        try {
            Platform.runLater(runnable);
        } catch (IllegalStateException e) {

            System.err.println(
                    "EventBus (JavaFX): Failed to post to JavaFX Application Thread. "
                            + "Toolkit not initialized or shutting down. Runnable will not be executed. "
                            + e.getMessage());
        }
    }

    public static boolean isJavaFxAvailable() {
        try {
            Class.forName("javafx.application.Platform");

            Platform.isFxApplicationThread();
            return true;
        } catch (ClassNotFoundException | IllegalStateException | LinkageError e) {
            return false;
        }
    }
}
