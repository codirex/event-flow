package com.codirex.eventflow.android;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import com.codirex.eventflow.api.thread.MainThreadSupport;

/**
 * Android-specific implementation of {@link MainThreadSupport}.
 * This class uses Android's {@link Looper} and {@link Handler} to interact with the main UI thread.
 * It allows EventFlow to post tasks to the main thread for subscribers that are configured
 * with {@link com.codirex.eventflow.ThreadMode#MAIN}.
 * <p>
 * An instance of this class should be passed to
 * {@link com.codirex.eventflow.EventFlow.Builder#mainThreadSupport(MainThreadSupport)} when configuring
 * EventFlow for an Android application.
 */
public class AndroidMainThreadSupport implements MainThreadSupport {
    private final Handler mainThreadHandler;

    /**
     * Constructs a new AndroidMainThreadSupport.
     * It initializes a {@link Handler} associated with the main application {@link Looper}.
     *
     * @throws IllegalStateException if {@link Looper#getMainLooper()} returns null, which can happen
     *                               if the Android environment is not fully set up (e.g., in some test scenarios
     *                               without a prepared Looper).
     */
    public AndroidMainThreadSupport() {
        Looper mainLooper = Looper.getMainLooper();
        if (mainLooper == null) {
            throw new IllegalStateException(
                    "EventBus (Android): Cannot initialize AndroidMainThreadSupport. Looper.getMainLooper() returned null. This usually means the Android environment is not fully initialized or you are in a test environment without a main looper.");
        }
        this.mainThreadHandler = new Handler(mainLooper);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation checks if the current thread is the Android main Looper's thread.
     * For API levels 23 (Marshmallow) and above, it uses {@link Looper#isCurrentThread()}.
     * For older versions, it compares the current thread with {@link Looper#getThread()}.
     *
     * @return True if the current thread is the Android main thread, false otherwise.
     *         Returns false if {@link Looper#getMainLooper()} is null.
     */
    @Override
    public boolean isMainThread() {
        Looper mainLooper = Looper.getMainLooper();
        if (mainLooper == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return mainLooper.isCurrentThread();
        } else {
            return Thread.currentThread() == mainLooper.getThread();
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation uses the internal {@link Handler} (initialized with the main {@link Looper})
     * to post the {@link Runnable} for execution on the main thread.
     * If the runnable is null, a message is printed to System.err and the runnable is ignored.
     *
     * @param runnable The runnable task to execute on the main thread.
     */
    @Override
    public void postToMainThread(Runnable runnable) {
        if (runnable == null) {
            System.err.println(
                    "EventBus (Android): Null runnable provided to postToMainThread. Ignoring.");
            return;
        }
        mainThreadHandler.post(runnable);
    }
}
