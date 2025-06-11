package com.codirex.eventflow.api;

public abstract class Cancelable {

    private boolean isCanceled;

    public boolean isCanceled() {
        return this.isCanceled;
    }

    public void setIsCanceled(boolean isCanceled) {
        this.isCanceled = isCanceled;
    }

}
