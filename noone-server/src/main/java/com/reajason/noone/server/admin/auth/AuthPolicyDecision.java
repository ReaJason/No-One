package com.reajason.noone.server.admin.auth;

import org.springframework.http.HttpStatus;

public record AuthPolicyDecision(HttpStatus httpStatus, String code, String message) {
}
