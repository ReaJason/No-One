package com.reajason.noone.core.client;


public class ResponseDecodeException extends ShellResponseException {
    public ResponseDecodeException(String message) {
        super(message);
    }

    public ResponseDecodeException(String message, Throwable cause) {
        super(message, cause);
    }
}
