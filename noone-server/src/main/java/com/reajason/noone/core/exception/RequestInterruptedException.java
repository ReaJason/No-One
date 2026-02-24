package com.reajason.noone.core.exception;

public class RequestInterruptedException extends ShellRequestException {
    public RequestInterruptedException(String message, Throwable cause) {
        super(message, false, cause);
    }
}
