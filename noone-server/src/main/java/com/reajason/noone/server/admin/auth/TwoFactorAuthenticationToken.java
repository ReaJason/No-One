package com.reajason.noone.server.admin.auth;

import lombok.Getter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

@Getter
public class TwoFactorAuthenticationToken extends UsernamePasswordAuthenticationToken {

    private final String twoFactorCode;

    public TwoFactorAuthenticationToken(Object principal, Object credentials, String twoFactorCode) {
        super(principal, credentials);
        this.twoFactorCode = twoFactorCode;
    }

    public TwoFactorAuthenticationToken(Object principal, Object credentials, String twoFactorCode,
            Collection<? extends GrantedAuthority> authorities) {
        super(principal, credentials, authorities);
        this.twoFactorCode = twoFactorCode;
    }

}
