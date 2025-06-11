package com.codirex.eventflow.api.thread;

public interface BackgroundPoster {
    void enqueue(Runnable runnable);
}
