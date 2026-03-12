package com.reajason.noone.server.admin.auth;

import lombok.Getter;

@Getter
public class UserNotActivatedException extends org.springframework.security.core.AuthenticationException {

    private final String setupToken;

    public UserNotActivatedException(String msg, String setupToken) {
        super(msg);
        this.setupToken = setupToken;
    }
}
