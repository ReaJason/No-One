package com.reajason.noone.core.client;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class HttpResponseException extends RuntimeException {
    private final int code;
    private final String message;

    public HttpResponseException(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
