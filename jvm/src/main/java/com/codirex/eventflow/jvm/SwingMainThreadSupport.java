package com.codirex.eventflow.jvm;

import com.codirex.eventflow.api.thread.MainThreadSupport;
import javax.swing.SwingUtilities;

public class SwingMainThreadSupport implements MainThreadSupport {
    public SwingMainThreadSupport() {}

    @Override
    public boolean isMainThread() {
        return SwingUtilities.isEventDispatchThread();
    }

    @Override
    public void postToMainThread(Runnable runnable) {
        if (runnable == null) {
            System.err.println(
                    "EventBus (Swing): Null runnable provided to postToMainThread. Ignoring.");
            return;
        }
        SwingUtilities.invokeLater(runnable);
    }

    public static boolean isSwingAvailable() {
        try {
            Class.forName("javax.swing.SwingUtilities");

            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
