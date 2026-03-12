package com.reajason.noone.server.admin.auth;

import com.reajason.noone.server.admin.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class DefaultAuthPolicy implements AuthPolicy {

    @Override
    public Optional<AuthPolicyDecision> evaluate(User user) {
        if (user == null || user.getStatus() == null) {
            return Optional.empty();
        }

        return switch (user.getStatus()) {
            case DISABLED -> Optional.of(new AuthPolicyDecision(
                    HttpStatus.FORBIDDEN,
                    "USER_DISABLED",
                    "User account is disabled"
            ));
            case LOCKED -> Optional.of(new AuthPolicyDecision(
                    HttpStatus.LOCKED,
                    "USER_LOCKED",
                    "User account is locked"
            ));
            case UNACTIVATED, ENABLED -> Optional.empty();
        };
    }
}
