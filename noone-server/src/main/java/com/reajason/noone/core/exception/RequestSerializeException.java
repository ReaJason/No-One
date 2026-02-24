package com.reajason.noone.core.exception;

public class RequestSerializeException extends ShellRequestException {
    public RequestSerializeException(String message, Throwable cause) {
        super(message, false, cause);
    }
}
