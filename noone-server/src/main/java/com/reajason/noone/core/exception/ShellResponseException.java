package com.reajason.noone.core.exception;

public class ShellResponseException extends ShellCommunicationException {
    public ShellResponseException(String message) {
        super(message, CommunicationPhase.RESPONSE, false);
    }

    public ShellResponseException(String message, Throwable cause) {
        super(message, cause, CommunicationPhase.RESPONSE, false);
    }
}
