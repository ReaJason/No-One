package com.reajason.noone.core.exception;

import com.reajason.noone.core.client.ShellRequestException;

public class RequestSerializeException extends ShellRequestException {
    public RequestSerializeException(String message, Throwable cause) {
        super(message, false, cause);
    }
}
