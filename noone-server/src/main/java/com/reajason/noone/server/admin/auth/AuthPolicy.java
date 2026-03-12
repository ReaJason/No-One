package com.reajason.noone.server.admin.auth;

import com.reajason.noone.server.admin.user.User;

import java.util.Optional;

public interface AuthPolicy {
    Optional<AuthPolicyDecision> evaluate(User user);
}
