package com.codirex.eventflow.api.thread;

public interface MainThreadSupport {

    boolean isMainThread();

    void postToMainThread(Runnable runnable);
}
