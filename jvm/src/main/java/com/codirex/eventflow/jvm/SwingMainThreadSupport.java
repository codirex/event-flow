package com.codirex.eventflow.jvm;

import com.codirex.eventflow.api.thread.MainThreadSupport;
import javax.swing.SwingUtilities;

/**
 * A JVM-specific implementation of {@link MainThreadSupport} for Swing applications.
 * This class uses {@link SwingUtilities} to interact with the Swing Event Dispatch Thread (EDT).
 * It allows EventFlow to post tasks to the EDT for subscribers that are configured
 * with {@link com.codirex.eventflow.ThreadMode#MAIN}.
 * <p>
 * An instance of this class can be passed to
 * {@link com.codirex.eventflow.EventFlow.Builder#mainThreadSupport(MainThreadSupport)} when configuring
 * EventFlow for a Swing-based desktop application.
 * Use {@link #isSwingAvailable()} to check if Swing environment is present before instantiation.
 */
public class SwingMainThreadSupport implements MainThreadSupport {
    /**
     * Default constructor for SwingMainThreadSupport.
     */
    public SwingMainThreadSupport() {}

    /**
     * {@inheritDoc}
     * <p>
     * This implementation uses {@link SwingUtilities#isEventDispatchThread()} to determine
     * if the current thread is the Swing Event Dispatch Thread.
     *
     * @return True if the current thread is the Swing EDT, false otherwise.
     */
    @Override
    public boolean isMainThread() {
        return SwingUtilities.isEventDispatchThread();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation uses {@link SwingUtilities#invokeLater(Runnable)} to queue
     * the {@link Runnable} for execution on the Swing Event Dispatch Thread.
     * If the runnable is null, a message is printed to System.err and the runnable is ignored.
     *
     * @param runnable The runnable task to execute on the Swing EDT.
     */
    @Override
    public void postToMainThread(Runnable runnable) {
        if (runnable == null) {
            System.err.println(
                    "EventBus (Swing): Null runnable provided to postToMainThread. Ignoring.");
            return;
        }
        SwingUtilities.invokeLater(runnable);
    }

    /**
     * Checks if the Swing environment is available (i.e., if {@code javax.swing.SwingUtilities} class can be loaded).
     * This can be used to conditionally create an instance of {@link SwingMainThreadSupport}.
     *
     * @return True if Swing classes are available, false otherwise.
     */
    public static boolean isSwingAvailable() {
        try {
            Class.forName("javax.swing.SwingUtilities");

            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
