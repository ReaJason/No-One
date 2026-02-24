package com.reajason.noone.core.exception;

public class ResponseDecodeException extends ShellResponseException {
    public ResponseDecodeException(String message) {
        super(message);
    }

    public ResponseDecodeException(String message, Throwable cause) {
        super(message, cause);
    }
}
