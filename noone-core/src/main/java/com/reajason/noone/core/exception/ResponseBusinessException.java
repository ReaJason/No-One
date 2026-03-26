package com.reajason.noone.core.exception;

import com.reajason.noone.core.client.ShellResponseException;

public class ResponseBusinessException extends ShellResponseException {
    public ResponseBusinessException(String message) {
        super(message);
    }
}
