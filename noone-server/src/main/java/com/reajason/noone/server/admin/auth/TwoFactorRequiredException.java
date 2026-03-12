package com.reajason.noone.server.admin.auth;

import org.springframework.security.core.AuthenticationException;

public class TwoFactorRequiredException extends AuthenticationException {

    public TwoFactorRequiredException() {
        super("Authenticator code required");
    }
}
