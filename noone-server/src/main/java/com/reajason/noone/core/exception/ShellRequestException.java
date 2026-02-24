package com.reajason.noone.core.exception;

public class ShellRequestException extends ShellCommunicationException {
    public ShellRequestException(String message, boolean retriable) {
        super(message, CommunicationPhase.REQUEST, retriable);
    }

    public ShellRequestException(String message, boolean retriable, Throwable cause) {
        super(message, cause, CommunicationPhase.REQUEST, retriable);
    }
}
