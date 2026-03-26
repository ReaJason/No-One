package com.reajason.noone.core.client;

import lombok.Getter;

@Getter
public class RequestSendException extends ShellRequestException {
    private final int attempts;

    public RequestSendException(String message, int attempts, Throwable cause) {
        super(message, true, cause);
        this.attempts = attempts;
    }

}
