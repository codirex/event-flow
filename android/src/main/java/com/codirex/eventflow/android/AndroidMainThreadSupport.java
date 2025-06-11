package com.codirex.eventflow.android;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import com.codirex.eventflow.api.thread.MainThreadSupport;

public class AndroidMainThreadSupport implements MainThreadSupport {
    private final Handler mainThreadHandler;

    public AndroidMainThreadSupport() {
        Looper mainLooper = Looper.getMainLooper();
        if (mainLooper == null) {
            throw new IllegalStateException(
                    "EventBus (Android): Cannot initialize AndroidMainThreadSupport. Looper.getMainLooper() returned null. This usually means the Android environment is not fully initialized or you are in a test environment without a main looper.");
        }
        this.mainThreadHandler = new Handler(mainLooper);
    }

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
