package com.reajason.noone.core.exception;

public class RequestSendException extends ShellRequestException {
    private final int attempts;

    public RequestSendException(String message, int attempts, Throwable cause) {
        super(message, true, cause);
        this.attempts = attempts;
    }

    public int getAttempts() {
        return attempts;
    }
}
