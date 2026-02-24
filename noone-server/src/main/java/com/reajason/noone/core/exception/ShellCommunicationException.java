package com.reajason.noone.core.exception;

public abstract class ShellCommunicationException extends RuntimeException {
    private final CommunicationPhase phase;
    private final boolean retriable;

    protected ShellCommunicationException(String message, CommunicationPhase phase, boolean retriable) {
        super(message);
        this.phase = phase;
        this.retriable = retriable;
    }

    protected ShellCommunicationException(String message, Throwable cause, CommunicationPhase phase, boolean retriable) {
        super(message, cause);
        this.phase = phase;
        this.retriable = retriable;
    }

    public CommunicationPhase getPhase() {
        return phase;
    }

    public boolean isRetriable() {
        return retriable;
    }
}
