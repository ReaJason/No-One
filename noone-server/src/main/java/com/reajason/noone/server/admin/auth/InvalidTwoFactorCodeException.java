package com.reajason.noone.server.admin.auth;

import org.springframework.security.authentication.BadCredentialsException;

public class InvalidTwoFactorCodeException extends BadCredentialsException {

    public InvalidTwoFactorCodeException() {
        super("Invalid authenticator code");
    }
}
