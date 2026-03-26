package com.reajason.noone.core.client;

public class RequestInterruptedException extends ShellRequestException {
    public RequestInterruptedException(String message, Throwable cause) {
        super(message, false, cause);
    }
}
