package com.reajason.noone.core.client;

import lombok.Getter;

@Getter
public class ResponseStatusException extends ShellResponseException {
    private final int expectedStatusCode;
    private final int actualStatusCode;

    public ResponseStatusException(int expectedStatusCode, int actualStatusCode) {
        super("Unexpected HTTP status code: expected=" + expectedStatusCode + ", actual=" + actualStatusCode);
        this.expectedStatusCode = expectedStatusCode;
        this.actualStatusCode = actualStatusCode;
    }

}
